package com.delaynore.fitty.ui.settings

import com.delaynore.fitty.data.updates.*
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.domain.StorageException
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private object NoopSessionManager : UpdateSessionManager {
    override fun canRequestInstall() = false
    override suspend fun commitInstall(apk: File, expectedVersionCode: Long) = null
    override suspend fun abandonSession(sessionId: Int) = Unit
    override fun hasSession(sessionId: Int) = false
    override fun hasConfirmation(sessionId: Int, requestId: Long) = false
}

private object NoopAttemptStore : UpdateAttemptStore {
    override fun read() = null
    override fun write(attempt: UpdateAttempt) = Unit
    override fun clear() = Unit
}

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
    data class Ready(val apk: File, val expectedVersionCode: Long = Long.MIN_VALUE) : UpdateInstallState
    data class RequestingPermission(val apk: File, val expectedVersionCode: Long) : UpdateInstallState
    data class PermissionDenied(val apk: File, val expectedVersionCode: Long) : UpdateInstallState
    data object PreparingInstall : UpdateInstallState
    data class PendingConfirmation(val sessionId: Int, val requestId: Long) : UpdateInstallState
    data class Installing(val sessionId: Int) : UpdateInstallState
    data class DownloadFailed(val reason: UpdateDownloadFailure) : UpdateInstallState
    data object WorkoutCheckFailed : UpdateInstallState
    data object InstallerUnavailable : UpdateInstallState
    data object InstallCancelled : UpdateInstallState
    data object InstallFailed : UpdateInstallState

    val canStartAnotherUpdate: Boolean get() = when (this) {
        Idle, is DownloadFailed, WorkoutCheckFailed, InstallerUnavailable, InstallCancelled, InstallFailed -> true
        else -> false
    }
    val canCheckForUpdates: Boolean get() = canStartAnotherUpdate || this is PermissionDenied
}

internal data class SettingsUiState(
    val installedApp: InstalledApp,
    val updateCheck: UpdateCheckState = UpdateCheckState.Idle,
    val updateInstall: UpdateInstallState = UpdateInstallState.Idle,
)

internal class UpdateCoordinator(
    installedApp: InstalledApp,
    private val updateService: UpdateService,
    private val installer: UpdateSessionManager,
    private val attempts: UpdateAttemptStore,
    private val workouts: WorkoutsRepository,
    private val scope: CoroutineScope,
    pendingPermissionPath: String? = null,
    pendingPermissionVersionCode: Long? = null,
) {
    constructor(
        installedApp: InstalledApp,
        updateService: UpdateService,
        workouts: WorkoutsRepository,
        scope: CoroutineScope,
        pendingPermissionPath: String? = null,
    ) : this(installedApp, updateService, NoopSessionManager, NoopAttemptStore, workouts, scope, pendingPermissionPath)

    private val pendingPermissionApk = pendingPermissionPath?.let(::File)
    private val state = MutableStateFlow(SettingsUiState(installedApp, updateInstall =
        if (pendingPermissionApk != null && pendingPermissionVersionCode != null)
            UpdateInstallState.RequestingPermission(pendingPermissionApk, pendingPermissionVersionCode)
        else UpdateInstallState.Idle))
    val uiState = state.asStateFlow()
    private val pruneDownloads = scope.launch { updateService.pruneAbandonedDownloads(pendingPermissionApk) }
    private var downloadJob: Job? = null

    init { reconcileInstall() }

    fun checkForUpdate() {
        if (state.value.updateCheck == UpdateCheckState.Checking || !state.value.updateInstall.canCheckForUpdates) return
        (state.value.updateInstall as? UpdateInstallState.PermissionDenied)?.apk?.let { scope.launch { updateService.deleteApk(it) } }
        state.update { it.copy(updateInstall = UpdateInstallState.Idle, updateCheck = UpdateCheckState.Checking) }
        scope.launch { state.update { it.copy(updateCheck = UpdateCheckState.Checked(updateService.checkForUpdate(it.installedApp.versionCode))) } }
    }

    fun startUpdate(hasUnsavedInput: Boolean) {
        val update = availableUpdate() ?: return
        if (!state.value.updateInstall.canStartAnotherUpdate) return
        setInstall(UpdateInstallState.CheckingWorkout)
        scope.launch {
            try {
                if (hasUnsavedInput || workouts.hasInProgressWorkout()) setInstall(UpdateInstallState.Warning) else download(update)
            } catch (_: StorageException) { setInstall(UpdateInstallState.WorkoutCheckFailed) }
        }
    }

    fun dismissWarning() { if (state.value.updateInstall == UpdateInstallState.Warning) setInstall(UpdateInstallState.Idle) }
    fun confirmDownload() { if (state.value.updateInstall == UpdateInstallState.Warning) availableUpdate()?.let(::download) }
    fun cancelDownload() {
        if (state.value.updateInstall !is UpdateInstallState.Downloading) return
        val attempt = downloadJob
        downloadJob = null
        setInstall(UpdateInstallState.Idle)
        attempt?.cancel()
    }
    fun continueInstallation() {
        val waiting = state.value.updateInstall as? UpdateInstallState.PermissionDenied ?: return
        setInstall(UpdateInstallState.Ready(waiting.apk, waiting.expectedVersionCode))
    }
    fun beginInstallation(ready: UpdateInstallState.Ready) {
        if (state.value.updateInstall != ready) return
        if (!installer.canRequestInstall()) {
            setInstall(UpdateInstallState.RequestingPermission(ready.apk, ready.expectedVersionCode))
            return
        }
        setInstall(UpdateInstallState.PreparingInstall)
        scope.launch {
            val session = installer.commitInstall(ready.apk, ready.expectedVersionCode)
            updateService.deleteApk(ready.apk)
            setInstall(if (session == null) UpdateInstallState.InstallerUnavailable
                else UpdateInstallState.PendingConfirmation(session.sessionId, session.requestId))
        }
    }
    fun onInstallPermissionReturned() {
        val waiting = state.value.updateInstall as? UpdateInstallState.RequestingPermission ?: return
        setInstall(if (installer.canRequestInstall()) UpdateInstallState.Ready(waiting.apk, waiting.expectedVersionCode)
            else UpdateInstallState.PermissionDenied(waiting.apk, waiting.expectedVersionCode))
    }
    fun confirmationLaunched(sessionId: Int, requestId: Long) {
        if (state.value.updateInstall == UpdateInstallState.PendingConfirmation(sessionId, requestId)) {
            attempts.read()?.takeIf {
                it.sessionId == sessionId && it.requestId == requestId && it.outcome == null
            }?.let { attempts.write(it.copy(phase = UpdateAttemptPhase.Installing)) }
            setInstall(UpdateInstallState.Installing(sessionId))
        }
    }
    fun installerUnavailable() { setInstall(UpdateInstallState.InstallerUnavailable) }

    fun reconcileInstall() {
        scope.launch {
            val attempt = attempts.read() ?: return@launch
            val reconciled = when {
                state.value.installedApp.versionCode >= attempt.expectedVersionCode -> UpdateInstallState.Idle to true
                attempt.outcome == InstallOutcome.Success -> UpdateInstallState.Idle to true
                attempt.outcome == InstallOutcome.Aborted -> UpdateInstallState.InstallCancelled to true
                attempt.outcome == InstallOutcome.Failure -> UpdateInstallState.InstallFailed to true
                attempt.phase == UpdateAttemptPhase.PendingConfirmation &&
                    installer.hasConfirmation(attempt.sessionId, attempt.requestId) ->
                    UpdateInstallState.PendingConfirmation(attempt.sessionId, attempt.requestId) to false
                attempt.phase == UpdateAttemptPhase.Installing && installer.hasSession(attempt.sessionId) ->
                    UpdateInstallState.Installing(attempt.sessionId) to false
                else -> {
                    installer.abandonSession(attempt.sessionId)
                    UpdateInstallState.InstallFailed to true
                }
            }
            if (reconciled.second) attempts.clear()
            setInstall(reconciled.first)
        }
    }

    val pendingPermissionPath: String? get() = (state.value.updateInstall as? UpdateInstallState.RequestingPermission)?.apk?.path
    val pendingPermissionVersionCode: Long? get() = (state.value.updateInstall as? UpdateInstallState.RequestingPermission)?.expectedVersionCode
    private fun availableUpdate() = ((state.value.updateCheck as? UpdateCheckState.Checked)?.result as? UpdateCheckResult.Available)?.update
    private fun download(update: FittyUpdate) {
        setInstall(UpdateInstallState.Downloading())
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val attempt = currentCoroutineContext().job
            pruneDownloads.join()
            val result = updateService.download(update, state.value.installedApp.versionCode) { percent ->
                scope.launch { if (attempt.isActive && state.value.updateInstall is UpdateInstallState.Downloading) setInstall(UpdateInstallState.Downloading(percent)) }
            }
            if (!attempt.isActive) {
                (result as? UpdateDownloadResult.Downloaded)?.apk?.let { updateService.deleteApk(it) }
                return@launch
            }
            downloadJob = null
            setInstall(when (result) {
                is UpdateDownloadResult.Downloaded -> UpdateInstallState.Ready(result.apk, update.versionCode)
                is UpdateDownloadResult.Failed -> UpdateInstallState.DownloadFailed(result.reason)
            })
        }
        downloadJob = job
        job.start()
    }
    private fun setInstall(install: UpdateInstallState) { state.update { it.copy(updateInstall = install) } }
}
