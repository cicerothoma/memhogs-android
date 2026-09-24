package dev.collinsthomas.memhogs.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import dev.collinsthomas.memhogs.BuildConfig
import dev.collinsthomas.memhogs.IShellService
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

private const val PERMISSION_REQUEST_CODE = 1
private const val TAG = "ShizukuConnection"

/**
 * Tracks whether Shizuku is running and authorized, and binds [ShellService]
 * once it is. Shizuku's listeners are process-wide, so one instance should
 * live as long as the screen that needs the shell.
 */
class ShizukuConnection(private val listener: Listener) {

    interface Listener {
        fun onAccessChanged(access: ShizukuAccess)
        fun onShellConnected()
        fun onShellFailed(error: Throwable)
    }

    var shell: IShellService? = null
        private set

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            shell = IShellService.Stub.asInterface(binder)
            listener.onShellConnected()
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
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> evaluateAccess() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    fun stop() {
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
        if (access == ShizukuAccess.READY && shell == null) bindShell()
    }

    private fun currentAccess(): ShizukuAccess = when {
        !Shizuku.pingBinder() -> ShizukuAccess.NOT_RUNNING
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuAccess.READY
        else -> ShizukuAccess.NEEDS_PERMISSION
    }

    private fun bindShell() {
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (e: RuntimeException) {
            listener.onShellFailed(e)
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
}
