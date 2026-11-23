package com.delaynore.fitty.ui.settings

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

private const val PERMISSION_APK_PATH = "updatePermissionApkPath"

internal data class InstalledApp(val versionName: String, val versionCode: Long)

internal sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Checked(val result: UpdateCheckResult) : UpdateCheckState
}

internal sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data object CheckingWorkout : UpdateInstallState
    data object Warning : UpdateInstallState
    data class Downloading(val percent: Int? = null) : UpdateInstallState
    data class Ready(val apk: File) : UpdateInstallState
    data class RequestingPermission(val apk: File) : UpdateInstallState
    data class PermissionDenied(val apk: File) : UpdateInstallState
    data class Installing(val apk: File) : UpdateInstallState
    data class DownloadFailed(val reason: UpdateDownloadFailure) : UpdateInstallState
    data object WorkoutCheckFailed : UpdateInstallState
    data object InstallerUnavailable : UpdateInstallState
    data object InstallCancelled : UpdateInstallState
    data object InstallFailed : UpdateInstallState

    val canStartAnotherUpdate: Boolean
        get() = when (this) {
            Idle, is DownloadFailed, WorkoutCheckFailed, InstallerUnavailable, InstallCancelled, InstallFailed -> true
            else -> false
        }

    val canCheckForUpdates: Boolean
        get() = canStartAnotherUpdate || this is PermissionDenied
}

internal data class SettingsUiState(
    val installedApp: InstalledApp,
    val updateCheck: UpdateCheckState = UpdateCheckState.Idle,
    val updateInstall: UpdateInstallState = UpdateInstallState.Idle,
)

internal class SettingsViewModel(
    installedApp: InstalledApp,
    private val updateService: UpdateService,
    private val workouts: WorkoutsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val pendingPermissionApk = savedState.get<String>(PERMISSION_APK_PATH)?.let(::File)
    private val state = MutableStateFlow(SettingsUiState(
        installedApp = installedApp,
        updateInstall = pendingPermissionApk?.let { UpdateInstallState.RequestingPermission(it) } ?: UpdateInstallState.Idle,
    ))
    val uiState = state.asStateFlow()

    private val pruneDownloads = viewModelScope.launch { updateService.pruneAbandonedDownloads(pendingPermissionApk) }
    private var downloadJob: Job? = null

    fun checkForUpdate() {
        if (state.value.updateCheck == UpdateCheckState.Checking || !state.value.updateInstall.canCheckForUpdates) return
        (state.value.updateInstall as? UpdateInstallState.PermissionDenied)?.apk?.delete()
        state.update { it.copy(updateInstall = UpdateInstallState.Idle, updateCheck = UpdateCheckState.Checking) }
        viewModelScope.launch {
            val result = updateService.checkForUpdate(state.value.installedApp.versionCode)
            state.update { it.copy(updateCheck = UpdateCheckState.Checked(result)) }
        }
    }

    fun startUpdate(hasUnsavedInput: Boolean) {
        val update = availableUpdate() ?: return
        if (!state.value.updateInstall.canStartAnotherUpdate) return
        setInstall(UpdateInstallState.CheckingWorkout)
        viewModelScope.launch {
            try {
                if (hasUnsavedInput || workouts.hasInProgressWorkout()) {
                    setInstall(UpdateInstallState.Warning)
                } else {
                    download(update)
                }
            } catch (_: SQLiteException) {
                setInstall(UpdateInstallState.WorkoutCheckFailed)
            }
        }
    }

    fun dismissWarning() {
        if (state.value.updateInstall == UpdateInstallState.Warning) setInstall(UpdateInstallState.Idle)
    }

    fun confirmDownload() {
        val update = availableUpdate() ?: return
        if (state.value.updateInstall != UpdateInstallState.Warning) return
        download(update)
    }

    fun cancelDownload() {
        if (state.value.updateInstall !is UpdateInstallState.Downloading) return
        val attempt = downloadJob
        downloadJob = null
        setInstall(UpdateInstallState.Idle)
        attempt?.cancel()
    }

    fun continueInstallation() {
        val waiting = state.value.updateInstall as? UpdateInstallState.PermissionDenied ?: return
        setInstall(UpdateInstallState.Ready(waiting.apk))
    }

    fun openInstaller(
        ready: UpdateInstallState.Ready,
        launchPermission: (Intent) -> Unit,
        launchInstaller: (Intent) -> Unit,
    ) {
        if (state.value.updateInstall != ready) return
        if (updateService.canRequestInstall()) {
            setInstall(UpdateInstallState.Installing(ready.apk))
            if (!updateService.openInstaller(ready.apk, launchInstaller)) failToOpenInstaller(ready.apk)
        } else {
            savedState[PERMISSION_APK_PATH] = ready.apk.path
            setInstall(UpdateInstallState.RequestingPermission(ready.apk))
            if (!updateService.requestInstallPermission(launchPermission)) failToOpenInstaller(ready.apk)
        }
    }

    fun onInstallPermissionReturned() {
        val requesting = state.value.updateInstall as? UpdateInstallState.RequestingPermission ?: return
        savedState.remove<String>(PERMISSION_APK_PATH)
        setInstall(if (updateService.canRequestInstall()) {
            UpdateInstallState.Ready(requesting.apk)
        } else {
            UpdateInstallState.PermissionDenied(requesting.apk)
        })
    }

    fun onInstallationReturned(resultCode: Int) {
        val installing = state.value.updateInstall as? UpdateInstallState.Installing ?: return
        installing.apk.delete()
        setInstall(when (resultCode) {
            Activity.RESULT_OK -> UpdateInstallState.Idle
            Activity.RESULT_CANCELED -> UpdateInstallState.InstallCancelled
            else -> UpdateInstallState.InstallFailed
        })
    }

    override fun onCleared() {
        when (val state = state.value.updateInstall) {
            is UpdateInstallState.Ready -> state.apk.delete()
            is UpdateInstallState.PermissionDenied -> state.apk.delete()
            else -> Unit
        }
    }

    private fun availableUpdate(): FittyUpdate? {
        val result = (state.value.updateCheck as? UpdateCheckState.Checked)?.result
        return (result as? UpdateCheckResult.Available)?.update
    }

    private fun download(update: FittyUpdate) {
        setInstall(UpdateInstallState.Downloading())
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val attempt = currentCoroutineContext().job
            pruneDownloads.join()
            val result = updateService.download(update, state.value.installedApp.versionCode) { percent ->
                viewModelScope.launch {
                    if (attempt.isActive && state.value.updateInstall is UpdateInstallState.Downloading) {
                        setInstall(UpdateInstallState.Downloading(percent))
                    }
                }
            }
            if (!attempt.isActive) {
                (result as? UpdateDownloadResult.Downloaded)?.apk?.delete()
                return@launch
            }
            downloadJob = null
            setInstall(when (result) {
                is UpdateDownloadResult.Downloaded -> UpdateInstallState.Ready(result.apk)
                is UpdateDownloadResult.Failed -> UpdateInstallState.DownloadFailed(result.reason)
            })
        }
        downloadJob = job
        job.start()
    }

    private fun setInstall(install: UpdateInstallState) {
        state.update { it.copy(updateInstall = install) }
    }

    private fun failToOpenInstaller(apk: File) {
        savedState.remove<String>(PERMISSION_APK_PATH)
        apk.delete()
        setInstall(UpdateInstallState.InstallerUnavailable)
    }
}
