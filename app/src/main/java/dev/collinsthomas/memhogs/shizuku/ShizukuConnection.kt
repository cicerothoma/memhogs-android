package dev.collinsthomas.memhogs.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dev.collinsthomas.memhogs.BuildConfig
import dev.collinsthomas.memhogs.IShellService
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

private const val PERMISSION_REQUEST_CODE = 1
private const val TAG = "ShizukuConnection"
private const val SHELL_START_TIMEOUT_MILLIS = 10_000L
private const val NEEDS_PROCESS_SHELL = "needs_process_shell"

class ShizukuConnection(private val listener: Listener, private val prefs: SharedPreferences) {

    interface Listener {
        fun onAccessChanged(access: ShizukuAccess)
        fun onShellConnected()
        fun onShellFailed(error: Throwable)
        fun onShellTimedOut()
    }

    var shell: IShellService? = null
        private set

    private var awaitingShell = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shellStartTimedOut = Runnable {
        awaitingShell = false
        if (shell == null) useProcessShell()
    }

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            stopAwaitingShell()
            prefs.edit().putBoolean(NEEDS_PROCESS_SHELL, false).apply()
            shell = IShellService.Stub.asInterface(binder)
            listener.onShellConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            shell = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { evaluateAccess() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        stopAwaitingShell()
        shell = null
        evaluateAccess()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> evaluateAccess() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    fun stop() {
        stopAwaitingShell()
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (shell != null) unbindShell()
    }

    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun evaluateAccess() {
        val access = currentAccess()
        listener.onAccessChanged(access)
        if (access == ShizukuAccess.READY && shell == null && !awaitingShell) bindShell()
    }

    private fun currentAccess(): ShizukuAccess = when {
        !Shizuku.pingBinder() -> ShizukuAccess.NOT_RUNNING
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuAccess.READY
        else -> ShizukuAccess.NEEDS_PERMISSION
    }

    private fun bindShell() {
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            awaitingShell = true
            mainHandler.postDelayed(shellStartTimedOut, SHELL_START_TIMEOUT_MILLIS)
            // Still bound above, so a Shizuku or OS update that fixes the starter switches back.
            if (prefs.getBoolean(NEEDS_PROCESS_SHELL, false)) useProcessShell()
        } catch (e: RuntimeException) {
            listener.onShellFailed(e)
        }
    }

    // HyperOS 3 crashes Shizuku's user service starter in LoadedApk.makeApplicationInner,
    // so run each command as a Shizuku remote process instead, which skips that starter.
    private fun useProcessShell() {
        try {
            val newProcess = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
            shell = ShellService { command -> newProcess.invoke(null, command, null, null) as Process }
            prefs.edit().putBoolean(NEEDS_PROCESS_SHELL, true).apply()
            listener.onShellConnected()
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Shizuku has no remote process fallback", e)
            listener.onShellTimedOut()
        }
    }

    private fun unbindShell() {
        try {
            Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Shizuku was gone before the shell could be unbound", e)
        }
        shell = null
    }

    private fun stopAwaitingShell() {
        awaitingShell = false
        mainHandler.removeCallbacks(shellStartTimedOut)
    }
}
