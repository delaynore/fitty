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

internal class SettingsViewModel(
    val installedApp: InstalledApp,
    private val updateService: UpdateService,
    private val workouts: WorkoutsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val check = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheck = check.asStateFlow()
    private val pendingPermissionApk = savedState.get<String>(PERMISSION_APK_PATH)?.let(::File)
    private val install = MutableStateFlow<UpdateInstallState>(
        pendingPermissionApk?.let { UpdateInstallState.RequestingPermission(it) }
            ?: UpdateInstallState.Idle,
    )
    val updateInstall = install.asStateFlow()
    private val pruneDownloads = viewModelScope.launch { updateService.pruneAbandonedDownloads(pendingPermissionApk) }
    private var downloadJob: Job? = null

    fun checkForUpdate() {
        if (check.value == UpdateCheckState.Checking || !install.value.canCheckForUpdates) return
        (install.value as? UpdateInstallState.PermissionDenied)?.apk?.delete()
        install.value = UpdateInstallState.Idle
        check.value = UpdateCheckState.Checking
        viewModelScope.launch {
            check.value = UpdateCheckState.Checked(updateService.checkForUpdate(installedApp.versionCode))
        }
    }

    fun startUpdate(hasUnsavedInput: Boolean) {
        val update = availableUpdate() ?: return
        if (!install.value.canStartAnotherUpdate) return
        install.value = UpdateInstallState.CheckingWorkout
        viewModelScope.launch {
            try {
                if (hasUnsavedInput || workouts.hasInProgressWorkout()) {
                    install.value = UpdateInstallState.Warning
                } else {
                    download(update)
                }
            } catch (_: SQLiteException) {
                install.value = UpdateInstallState.WorkoutCheckFailed
            }
        }
    }

    fun dismissWarning() {
        if (install.value == UpdateInstallState.Warning) install.value = UpdateInstallState.Idle
    }

    fun confirmDownload() {
        val update = availableUpdate() ?: return
        if (install.value != UpdateInstallState.Warning) return
        download(update)
    }

    fun cancelDownload() {
        if (install.value !is UpdateInstallState.Downloading) return
        val attempt = downloadJob
        downloadJob = null
        install.value = UpdateInstallState.Idle
        attempt?.cancel()
    }

    fun continueInstallation() {
        val waiting = install.value as? UpdateInstallState.PermissionDenied ?: return
        install.value = UpdateInstallState.Ready(waiting.apk)
    }

    fun openInstaller(
        ready: UpdateInstallState.Ready,
        launchPermission: (Intent) -> Unit,
        launchInstaller: (Intent) -> Unit,
    ) {
        if (install.value != ready) return
        if (updateService.canRequestInstall()) {
            install.value = UpdateInstallState.Installing(ready.apk)
            if (!updateService.openInstaller(ready.apk, launchInstaller)) failToOpenInstaller(ready.apk)
        } else {
            savedState[PERMISSION_APK_PATH] = ready.apk.path
            install.value = UpdateInstallState.RequestingPermission(ready.apk)
            if (!updateService.requestInstallPermission(launchPermission)) failToOpenInstaller(ready.apk)
        }
    }

    fun onInstallPermissionReturned() {
        val requesting = install.value as? UpdateInstallState.RequestingPermission ?: return
        savedState.remove<String>(PERMISSION_APK_PATH)
        install.value = if (updateService.canRequestInstall()) {
            UpdateInstallState.Ready(requesting.apk)
        } else {
            UpdateInstallState.PermissionDenied(requesting.apk)
        }
    }

    fun onInstallationReturned(resultCode: Int) {
        val installing = install.value as? UpdateInstallState.Installing ?: return
        installing.apk.delete()
        install.value = when (resultCode) {
            Activity.RESULT_OK -> UpdateInstallState.Idle
            Activity.RESULT_CANCELED -> UpdateInstallState.InstallCancelled
            else -> UpdateInstallState.InstallFailed
        }
    }

    override fun onCleared() {
        when (val state = install.value) {
            is UpdateInstallState.Ready -> state.apk.delete()
            is UpdateInstallState.PermissionDenied -> state.apk.delete()
            else -> Unit
        }
    }

    private fun availableUpdate(): FittyUpdate? {
        val result = (check.value as? UpdateCheckState.Checked)?.result
        return (result as? UpdateCheckResult.Available)?.update
    }

    private fun download(update: FittyUpdate) {
        install.value = UpdateInstallState.Downloading()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val attempt = currentCoroutineContext().job
            pruneDownloads.join()
            val result = updateService.download(update, installedApp.versionCode) { percent ->
                viewModelScope.launch {
                    if (attempt.isActive && install.value is UpdateInstallState.Downloading) {
                        install.value = UpdateInstallState.Downloading(percent)
                    }
                }
            }
            if (!attempt.isActive) {
                (result as? UpdateDownloadResult.Downloaded)?.apk?.delete()
                return@launch
            }
            downloadJob = null
            install.value = when (result) {
                is UpdateDownloadResult.Downloaded -> UpdateInstallState.Ready(result.apk)
                is UpdateDownloadResult.Failed -> UpdateInstallState.DownloadFailed(result.reason)
            }
        }
        downloadJob = job
        job.start()
    }

    private fun failToOpenInstaller(apk: File) {
        savedState.remove<String>(PERMISSION_APK_PATH)
        apk.delete()
        install.value = UpdateInstallState.InstallerUnavailable
    }
}
