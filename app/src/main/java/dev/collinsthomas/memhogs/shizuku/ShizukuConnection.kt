package dev.collinsthomas.memhogs.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
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

class ShizukuConnection(private val listener: Listener) {

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
        listener.onShellTimedOut()
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

    private fun stopAwaitingShell() {
        awaitingShell = false
        mainHandler.removeCallbacks(shellStartTimedOut)
    }
}
