package com.delaynore.fitty.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    val latestSettingsVisible by rememberUpdatedState(settingsVisible)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.onInstallPermissionReturned()
    }
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.reconcileInstall()
    }
    val visible = settingsVisible()
    LaunchedEffect(lifecycle, lifecycleState, visible) {
        if (visible && latestSettingsVisible() && lifecycleState == Lifecycle.State.RESUMED) {
            updates.reconcileInstall()
        }
    }
    val install = state.updateInstall
    val installKey = if (install is UpdateInstallState.Downloading) UpdateInstallState.Downloading::class else install
    LaunchedEffect(lifecycle, lifecycleState, visible, installKey) {
        if (!visible || !latestSettingsVisible() || lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        when (install) {
            is UpdateInstallState.Ready -> updates.beginInstallation(install)
            is UpdateInstallState.RequestingPermission -> try {
                permission.launch(installer.permissionIntent())
            } catch (_: ActivityNotFoundException) {
                updates.installerUnavailable()
            } catch (_: SecurityException) {
                updates.installerUnavailable()
            }
            is UpdateInstallState.PendingConfirmation -> {
                repeat(100) {
                    installer.takeConfirmation(install.sessionId, install.requestId)?.let { intent ->
                        try {
                            confirmation.launch(intent)
                            updates.confirmationLaunched(install.sessionId, install.requestId)
                        } catch (_: ActivityNotFoundException) {
                            updates.installerUnavailable()
                        } catch (_: SecurityException) {
                            updates.installerUnavailable()
                        }
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
