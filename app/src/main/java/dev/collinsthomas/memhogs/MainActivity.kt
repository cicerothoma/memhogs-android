package dev.collinsthomas.memhogs

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.collinsthomas.memhogs.mem.MeminfoParser
import dev.collinsthomas.memhogs.mem.groupByPackage
import dev.collinsthomas.memhogs.mem.humanKb
import dev.collinsthomas.memhogs.shizuku.ShellService
import dev.collinsthomas.memhogs.ui.Gauge
import dev.collinsthomas.memhogs.ui.MemhogsApp
import dev.collinsthomas.memhogs.ui.ShizukuAccess
import dev.collinsthomas.memhogs.ui.UiGroup
import dev.collinsthomas.memhogs.ui.UiMember
import dev.collinsthomas.memhogs.ui.UiSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Locale

private const val SHIZUKU_PERMISSION_REQUEST = 1
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

class MainActivity : ComponentActivity() {

    private var access by mutableStateOf(ShizukuAccess.NOT_RUNNING)
    private var snapshot by mutableStateOf<UiSnapshot?>(null)
    private var gauge by mutableStateOf<Gauge?>(null)
    private var refreshing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var shizukuInstalled by mutableStateOf(false)
    private var motion by mutableStateOf(true)
    private var reclaimMsg by mutableStateOf<String?>(null)

    /** Package, label, and pre-kill size of an `am kill` awaiting its delta. */
    private var pendingReclaim: Triple<String, String, Long>? = null

    private var shell: IShellService? = null

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shell = IShellService.Stub.asInterface(binder)
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shell = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { evaluateAccess() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        shell = null
        evaluateAccess()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> evaluateAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        setContent {
            MemhogsApp(
                access = access,
                snapshot = snapshot,
                gauge = gauge,
                refreshing = refreshing,
                error = error,
                reclaimMsg = reclaimMsg,
                shizukuInstalled = shizukuInstalled,
                motion = motion,
                onRefresh = {
                    readGauge()
                    evaluateAccess()
                    refresh()
                },
                onRequestPermission = { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) },
                onOpenShizuku = ::openShizuku,
                onGetShizuku = ::getShizukuFromGitHub,
                onReclaim = ::reclaim,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        readGauge()
        evaluateAccess()
        shizukuInstalled = isPackageInstalled(SHIZUKU_PACKAGE)
        // Honor the system's "remove animations" accessibility setting.
        motion = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) > 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (shell != null) {
            runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
            shell = null
        }
    }

    private fun evaluateAccess() {
        val next = when {
            !Shizuku.pingBinder() -> ShizukuAccess.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuAccess.READY
            else -> ShizukuAccess.NEEDS_PERMISSION
        }
        access = next
        if (next == ShizukuAccess.READY && shell == null) {
            runCatching { Shizuku.bindUserService(serviceArgs, connection) }
                .onFailure { error = it.message ?: it.toString() }
        }
    }

    /** Device-wide numbers from ActivityManager; works without Shizuku. */
    private fun readGauge() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        gauge = Gauge(totalBytes = info.totalMem, availBytes = info.availMem, low = info.lowMemory)
    }

    private fun refresh() {
        val svc = shell ?: return
        if (refreshing) return
        refreshing = true
        error = null
        lifecycleScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { svc.run("dumpsys meminfo") }
                val parsed = MeminfoParser.parse(raw)
                if (parsed.processes.isEmpty()) {
                    error = "dumpsys returned no per-process data"
                    return@launch
                }
                snapshot = withContext(Dispatchers.Default) { toUi(parsed) }
                resolvePendingReclaim()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                refreshing = false
            }
        }
    }

    /**
     * Kills [pkg]'s background processes via `am kill`, the same reclaim the
     * system performs under memory pressure, then refreshes and reports how
     * much came back. Foreground apps and active services are untouched.
     */
    private fun reclaim(pkg: String) {
        val svc = shell ?: return
        val before = snapshot?.groups?.find { it.key == pkg } ?: return
        pendingReclaim = Triple(pkg, before.label, before.memKb)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { svc.run("am kill $pkg") }
                delay(900) // give the kernel a beat to settle before measuring
                refresh()
            } catch (e: Exception) {
                pendingReclaim = null
                error = e.message ?: e.toString()
            }
        }
    }

    private fun resolvePendingReclaim() {
        val (pkg, label, before) = pendingReclaim ?: return
        pendingReclaim = null
        val after = snapshot?.groups?.find { it.key == pkg }?.memKb ?: 0L
        val delta = before - after
        reclaimMsg = if (delta > 0) {
            "$label: reclaimed ${humanKb(delta)}"
        } else {
            "$label: nothing to reclaim right now"
        }
        lifecycleScope.launch {
            delay(5000)
            reclaimMsg = null
        }
    }

    private fun toUi(parsed: MeminfoParser.Snapshot): UiSnapshot {
        // The owner-package walk probes several candidate names per process,
        // so cache PackageManager answers for the duration of one snapshot.
        val labelCache = mutableMapOf<String, String?>()
        val groups = groupByPackage(parsed.processes) { pkg ->
            labelCache.getOrPut(pkg) { appLabel(pkg) }
        }
        val totalKb = parsed.totalRamKb
        return UiSnapshot(
            totalKb = totalKb,
            usedKb = parsed.usedRamKb,
            totalText = humanKb(totalKb),
            usedText = humanKb(parsed.usedRamKb),
            usedFrac = if (totalKb > 0) (parsed.usedRamKb.toFloat() / totalKb) else 0f,
            processCount = parsed.processes.size,
            groups = groups.map { g ->
                val frac = if (totalKb > 0) g.pssKb.toDouble() / totalKb else 0.0
                UiGroup(
                    key = g.packageName,
                    label = g.label,
                    isApp = g.isApp,
                    mem = humanKb(g.pssKb),
                    memKb = g.pssKb,
                    pctFrac = frac,
                    pctText = String.format(Locale.US, "%.1f%%", frac * 100),
                    canReclaim = g.isApp && g.packageName != BuildConfig.APPLICATION_ID,
                    members = g.members.map { m ->
                        UiMember(name = m.processName, pid = m.pid, mem = humanKb(m.pssKb))
                    },
                )
            },
        )
    }

    private fun appLabel(pkg: String): String? = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            getShizukuFromGitHub()
        }
    }

    // The Play Store listing sometimes reports Shizuku as incompatible with
    // brand-new Android versions; the GitHub release installs fine.
    private fun getShizukuFromGitHub() {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases/latest"))
        )
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
