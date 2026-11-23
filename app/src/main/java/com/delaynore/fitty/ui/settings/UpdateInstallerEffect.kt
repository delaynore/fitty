package com.delaynore.fitty.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun UpdateInstallerEffect(viewModel: SettingsViewModel, settingsVisible: () -> Boolean) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onInstallPermissionReturned()
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onInstallationReturned(it.resultCode)
    }
    val ready = state.updateInstall as? UpdateInstallState.Ready
    val visible = settingsVisible()
    LaunchedEffect(lifecycle, ready, lifecycleState, visible) {
        if (ready != null && visible && settingsVisible() && lifecycle.currentState == Lifecycle.State.RESUMED) {
            viewModel.openInstaller(ready, { permission.launch(it) }, { installer.launch(it) })
        }
    }
}
