package dev.collinsthomas.memhogs

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import dev.collinsthomas.memhogs.shizuku.SHIZUKU_PACKAGE
import dev.collinsthomas.memhogs.ui.MemhogsApp

private const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases/latest"

class MainActivity : ComponentActivity() {

    private val viewModel: MemhogsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            MemhogsApp(
                state = state,
                onRefresh = viewModel::refresh,
                onRequestPermission = viewModel::requestPermission,
                onOpenShizuku = ::openShizuku,
                onGetShizuku = ::getShizukuFromGitHub,
                onReclaim = viewModel::reclaimBackgroundMemory,
                onForceStop = viewModel::forceStop,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume(animationsEnabled = animationsEnabled())
    }

    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            getShizukuFromGitHub()
        }
    }

    private fun getShizukuFromGitHub() {
        startActivity(Intent(Intent.ACTION_VIEW, SHIZUKU_RELEASES_URL.toUri()))
    }
}
