package com.delaynore.fitty.ui.settings

import com.delaynore.fitty.data.updates.*
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCoordinatorInstallTest {
    @Test
    fun committedApkIsDeletedAndWaitsForConfirmation() = runTest {
        val apk = File("update.apk")
        val service = FakeService(apk)
        val installer = FakeInstaller(session = InstallSession(7, 11))
        val coordinator = UpdateCoordinator(
            InstalledApp("1", 1), service, installer, MemoryAttemptStore(), FakeWorkoutsRepository(), this,
        )
        coordinator.checkForUpdate(); runCurrent()
        coordinator.startUpdate(false); runCurrent()
        val ready = coordinator.uiState.value.updateInstall as UpdateInstallState.Ready

        coordinator.beginInstallation(ready)
        assertEquals(UpdateInstallState.PreparingInstall, coordinator.uiState.value.updateInstall)
        runCurrent()

        assertEquals(UpdateInstallState.PendingConfirmation(7, 11), coordinator.uiState.value.updateInstall)
        assertEquals(listOf(apk), service.deleted)
    }

    @Test
    fun permissionDenialKeepsTheApk() = runTest {
        val apk = File("update.apk")
        val service = FakeService(apk)
        val installer = FakeInstaller(canInstall = false)
        val coordinator = UpdateCoordinator(
            InstalledApp("1", 1), service, installer, MemoryAttemptStore(), FakeWorkoutsRepository(), this,
        )
        coordinator.checkForUpdate(); runCurrent(); coordinator.startUpdate(false); runCurrent()
        coordinator.beginInstallation(coordinator.uiState.value.updateInstall as UpdateInstallState.Ready)
        coordinator.onInstallPermissionReturned()

        assertTrue(coordinator.uiState.value.updateInstall is UpdateInstallState.PermissionDenied)
        assertTrue(service.deleted.isEmpty())
    }

    @Test
    fun coldStartMapsTerminalOutcomesAndAbandonsALostSession() = runTest {
        val expected = mapOf(
            InstallOutcome.Success to UpdateInstallState.Idle,
            InstallOutcome.Aborted to UpdateInstallState.InstallCancelled,
            InstallOutcome.Failure to UpdateInstallState.InstallFailed,
        )
        expected.forEach { (outcome, state) ->
            val store = MemoryAttemptStore(UpdateAttempt(3, 2, outcome))
            val coordinator = UpdateCoordinator(
                InstalledApp("1", 1), FakeService(File("x")), FakeInstaller(), store,
                FakeWorkoutsRepository(), this,
            )
            runCurrent()
            assertEquals(state, coordinator.uiState.value.updateInstall)
            assertEquals(null, store.read())
        }

        val store = MemoryAttemptStore(UpdateAttempt(9, 2))
        val installer = FakeInstaller()
        val coordinator = UpdateCoordinator(
            InstalledApp("1", 1), FakeService(File("x")), installer, store, FakeWorkoutsRepository(), this,
        )
        runCurrent()
        assertEquals(UpdateInstallState.InstallFailed, coordinator.uiState.value.updateInstall)
        assertEquals(listOf(9), installer.abandoned)
    }

    @Test
    fun reconciliationKeepsAnInMemoryConfirmationPending() = runTest {
        val attempt = UpdateAttempt(9, 2, requestId = 17)
        val store = MemoryAttemptStore(attempt)
        val coordinator = UpdateCoordinator(
            InstalledApp("1", 1), FakeService(File("x")),
            FakeInstaller(hasConfirmation = true), store, FakeWorkoutsRepository(), this,
        )

        runCurrent()

        assertEquals(UpdateInstallState.PendingConfirmation(9, 17), coordinator.uiState.value.updateInstall)
        assertEquals(attempt, store.read())
    }

    @Test
    fun reconciliationKeepsALiveSessionInstalling() = runTest {
        val attempt = UpdateAttempt(9, 2, requestId = 17)
        val store = MemoryAttemptStore(attempt)
        val coordinator = UpdateCoordinator(
            InstalledApp("1", 1), FakeService(File("x")),
            FakeInstaller(hasSession = true), store, FakeWorkoutsRepository(), this,
        )

        runCurrent()

        assertEquals(UpdateInstallState.Installing(9), coordinator.uiState.value.updateInstall)
        assertEquals(attempt, store.read())
    }

    private class FakeService(private val apk: File) : UpdateService {
        val deleted = mutableListOf<File>()
        override suspend fun checkForUpdate(installedVersionCode: Long) = UpdateCheckResult.Available(
            FittyUpdate("2", 2, "https://github.com/delaynore/fitty/releases/download/x/fitty-2.apk"),
        )
        override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = Unit
        override suspend fun download(update: FittyUpdate, installedVersionCode: Long, onProgress: (Int) -> Unit) =
            UpdateDownloadResult.Downloaded(apk)
        override suspend fun deleteApk(apk: File) { deleted += apk }
    }

    private class FakeInstaller(
        var canInstall: Boolean = true,
        private val session: InstallSession? = null,
        private val hasSession: Boolean = false,
        private val hasConfirmation: Boolean = false,
    ) : UpdateSessionManager {
        val abandoned = mutableListOf<Int>()
        override fun canRequestInstall() = canInstall
        override suspend fun commitInstall(apk: File, expectedVersionCode: Long) = session
        override suspend fun abandonSession(sessionId: Int) { abandoned += sessionId }
        override fun hasSession(sessionId: Int) = hasSession
        override fun hasConfirmation(sessionId: Int, requestId: Long) = hasConfirmation
    }

    private class MemoryAttemptStore(private var attempt: UpdateAttempt? = null) : UpdateAttemptStore {
        override fun read() = attempt
        override fun write(attempt: UpdateAttempt) { this.attempt = attempt }
        override fun clear() { attempt = null }
    }
}
