package dev.collinsthomas.memhogs

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.collinsthomas.memhogs.mem.ActivityLruParser
import dev.collinsthomas.memhogs.mem.MeminfoParser
import dev.collinsthomas.memhogs.shizuku.SHIZUKU_PACKAGE
import dev.collinsthomas.memhogs.shizuku.ShizukuAccess
import dev.collinsthomas.memhogs.shizuku.ShizukuConnection
import dev.collinsthomas.memhogs.ui.Gauge
import dev.collinsthomas.memhogs.ui.LoadError
import dev.collinsthomas.memhogs.ui.MemhogsUiState
import dev.collinsthomas.memhogs.ui.PendingReclaim
import dev.collinsthomas.memhogs.ui.UiGroup
import dev.collinsthomas.memhogs.ui.UiSnapshot
import dev.collinsthomas.memhogs.ui.toUiSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECLAIM_SETTLE_MILLIS = 900L
private const val RECLAIM_MESSAGE_MILLIS = 5_000L

class MemhogsViewModel(application: Application) :
    AndroidViewModel(application),
    ShizukuConnection.Listener {

    private val packageManager = application.packageManager
    private val activityManager = application.getSystemService(ActivityManager::class.java)
    private val shizuku = ShizukuConnection(this)

    private val mutableState = MutableStateFlow(MemhogsUiState())
    val state: StateFlow<MemhogsUiState> = mutableState.asStateFlow()

    private var pendingReclaim: PendingReclaim? = null
    private var reclaimMessageJob: Job? = null

    init {
        shizuku.start()
    }

    fun onResume(animationsEnabled: Boolean) {
        mutableState.update {
            it.copy(
                motion = animationsEnabled,
                shizukuInstalled = isInstalled(SHIZUKU_PACKAGE),
                gauge = readGauge(),
            )
        }
        shizuku.evaluateAccess()
    }

    fun refresh() {
        mutableState.update { it.copy(gauge = readGauge(), error = null) }
        shizuku.evaluateAccess()
        loadSnapshot()
    }

    fun requestPermission() {
        shizuku.requestPermission()
    }

    fun reclaimBackgroundMemory(packageNames: Set<String>) {
        reclaim(packageNames, UiGroup::canReclaim, IShellService::killBackgroundProcesses)
    }

    fun forceStop(packageName: String) {
        reclaim(setOf(packageName), UiGroup::canForceStop, IShellService::forceStop)
    }

    private fun reclaim(
        packageNames: Set<String>,
        eligible: (UiGroup) -> Boolean,
        stop: IShellService.(String) -> Unit,
    ) {
        val shell = shizuku.shell ?: return
        val snapshot = state.value.snapshot ?: return
        val pending = PendingReclaim.of(snapshot, packageNames, eligible) ?: return
        pendingReclaim = pending
        viewModelScope.launch {
            reportingFailures {
                withContext(Dispatchers.IO) { pending.packageNames.forEach { shell.stop(it) } }
                delay(RECLAIM_SETTLE_MILLIS)
                loadSnapshot()
            }
        }
    }

    override fun onAccessChanged(access: ShizukuAccess) {
        mutableState.update { it.copy(access = access) }
    }

    override fun onShellConnected() {
        loadSnapshot()
    }

    override fun onShellFailed(error: Throwable) {
        showFailure(error)
    }

    override fun onShellTimedOut() {
        mutableState.update { it.copy(error = LoadError.ShellDidNotStart) }
    }

    override fun onCleared() {
        shizuku.stop()
    }

    private fun loadSnapshot() {
        val shell = shizuku.shell ?: return
        if (state.value.refreshing) return
        mutableState.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            reportingFailures { measure(shell) }
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun measure(shell: IShellService) {
        val (meminfo, activityProcesses) = withContext(Dispatchers.IO) {
            shell.meminfo() to shell.activityProcesses()
        }
        val parsed = MeminfoParser.parse(meminfo)
        if (parsed.processes.isEmpty()) {
            mutableState.update { it.copy(error = LoadError.EmptyMeminfo) }
            return
        }
        val snapshot = withContext(Dispatchers.Default) {
            parsed.toUiSnapshot(
                appLabelOf = ::appLabel,
                ownPackage = BuildConfig.APPLICATION_ID,
                isPersistentApp = ::isPersistentApp,
                hostPackageOfPid = hostPackagesOfIsolatedProcesses(activityProcesses),
            )
        }
        mutableState.update { it.copy(snapshot = snapshot) }
        resolvePendingReclaim(snapshot)
    }

    private fun resolvePendingReclaim(snapshot: UiSnapshot) {
        val pending = pendingReclaim ?: return
        pendingReclaim = null
        mutableState.update { it.copy(reclaimResult = pending.resultAgainst(snapshot)) }
        reclaimMessageJob?.cancel()
        reclaimMessageJob = viewModelScope.launch {
            delay(RECLAIM_MESSAGE_MILLIS)
            mutableState.update { it.copy(reclaimResult = null) }
        }
    }

    private inline fun reportingFailures(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showFailure(e)
        }
    }

    private fun showFailure(error: Throwable) {
        pendingReclaim = null
        mutableState.update { it.copy(error = LoadError.Failed(error.message ?: error.toString())) }
    }

    private fun readGauge(): Gauge {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return Gauge(totalBytes = info.totalMem, availBytes = info.availMem, low = info.lowMemory)
    }

    private fun hostPackagesOfIsolatedProcesses(activityProcesses: String): Map<Int, String> =
        ActivityLruParser.hostUidsOfIsolatedProcesses(activityProcesses)
            .mapNotNull { (pid, hostUid) -> packageOfUid(hostUid)?.let { pid to it } }
            .toMap()

    private fun packageOfUid(uid: Int): String? = packageManager.getPackagesForUid(uid)?.firstOrNull()

    private fun appLabel(packageName: String): String? =
        applicationInfo(packageName)?.let { packageManager.getApplicationLabel(it).toString() }

    private fun isInstalled(packageName: String): Boolean = applicationInfo(packageName) != null

    private fun isPersistentApp(packageName: String): Boolean =
        applicationInfo(packageName)?.let { it.flags and ApplicationInfo.FLAG_PERSISTENT != 0 } ?: false

    private fun applicationInfo(packageName: String): ApplicationInfo? = try {
        packageManager.getApplicationInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
