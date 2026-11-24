package com.delaynore.fitty.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.platform.updates.AndroidUpdateInstaller
import kotlinx.coroutines.delay

@Composable
internal fun UpdateInstallerEffect(
    updates: UpdateCoordinator,
    installer: AndroidUpdateInstaller,
    settingsVisible: () -> Boolean,
) {
    val state by updates.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.onInstallPermissionReturned()
    }
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.reconcileInstall()
    }
    val visible = settingsVisible()
    LaunchedEffect(lifecycleState, visible, state.updateInstall) {
        if (!visible || !settingsVisible() || lifecycle.currentState != Lifecycle.State.RESUMED) return@LaunchedEffect
        when (val install = state.updateInstall) {
            is UpdateInstallState.Ready -> updates.beginInstallation(install)
            is UpdateInstallState.RequestingPermission -> try {
                permission.launch(installer.permissionIntent())
            } catch (_: ActivityNotFoundException) { updates.installerUnavailable() }
            is UpdateInstallState.PendingConfirmation -> {
                repeat(100) {
                    installer.takeConfirmation(install.sessionId, install.requestId)?.let { intent ->
                        try {
                            confirmation.launch(intent)
                            updates.confirmationLaunched(install.sessionId, install.requestId)
                        } catch (_: ActivityNotFoundException) { updates.installerUnavailable() }
                        return@LaunchedEffect
                    }
                    delay(50)
                }
                updates.installerUnavailable()
            }
            else -> Unit
        }
    }
}
