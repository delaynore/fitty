package com.delaynore.fitty.ui.settings

import android.content.Intent
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCoordinatorTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun cancellingDiscardsLateProgressAndApkWithoutAffectingTheRetry() = runTest {
        val cancelledApk = folder.newFile("cancelled.apk")
        val retryApk = folder.newFile("retry.apk")
        val updates = DelayedDownloads()
        val coordinator = UpdateCoordinator(InstalledApp("1.0", 7), updates, FakeWorkoutsRepository(), this)

        try {
            coordinator.checkForUpdate()
            runCurrent()
            coordinator.startUpdate(hasUnsavedInput = false)
            runCurrent()
            assertEquals(UpdateInstallState.Downloading(), coordinator.uiState.value.updateInstall)

            coordinator.cancelDownload()
            runCurrent()
            assertEquals(UpdateInstallState.Idle, coordinator.uiState.value.updateInstall)

            coordinator.startUpdate(hasUnsavedInput = false)
            runCurrent()
            assertEquals(UpdateInstallState.Downloading(25), coordinator.uiState.value.updateInstall)

            updates.firstProgress.getCompleted()(80)
            updates.lateResult.complete(UpdateDownloadResult.Downloaded(cancelledApk))
            runCurrent()

            assertEquals(UpdateInstallState.Downloading(25), coordinator.uiState.value.updateInstall)
            assertFalse(cancelledApk.exists())

            updates.retryResult.complete(UpdateDownloadResult.Downloaded(retryApk))
            runCurrent()

            assertEquals(UpdateInstallState.Ready(retryApk), coordinator.uiState.value.updateInstall)
            assertEquals(1, updates.checks)
        } finally {
            updates.lateResult.complete(UpdateDownloadResult.Downloaded(cancelledApk))
            updates.retryResult.complete(UpdateDownloadResult.Downloaded(retryApk))
        }
    }

    @Test
    fun failedChecksCanBeRetriedUntilTheInstalledBuildIsUpToDate() = runTest {
        val results = UpdateCheckFailure.entries.map { UpdateCheckResult.Failed(it) } + UpdateCheckResult.UpToDate
        val pendingResults = results.iterator()
        val updates = FakeUpdates(check = { pendingResults.next() })
        val coordinator = UpdateCoordinator(InstalledApp("1.0", 7), updates, FakeWorkoutsRepository(), this)

        for (result in results) {
            coordinator.checkForUpdate()
            assertEquals(UpdateCheckState.Checking, coordinator.uiState.value.updateCheck)
            runCurrent()
            assertEquals(UpdateCheckState.Checked(result), coordinator.uiState.value.updateCheck)
        }
        assertEquals(results.size, updates.checks)
    }

    @Test
    fun failedDownloadsCanBeRetriedWithoutCheckingAgain() = runTest {
        val apk = folder.newFile("retry.apk")
        val failures = UpdateDownloadFailure.entries.iterator()
        val updates = FakeUpdates(downloadResult = {
            if (failures.hasNext()) UpdateDownloadResult.Failed(failures.next()) else UpdateDownloadResult.Downloaded(apk)
        })
        val coordinator = UpdateCoordinator(InstalledApp("1.0", 7), updates, FakeWorkoutsRepository(), this)
        coordinator.checkForUpdate()
        runCurrent()

        for (reason in UpdateDownloadFailure.entries) {
            coordinator.startUpdate(hasUnsavedInput = false)
            runCurrent()
            assertEquals(UpdateInstallState.DownloadFailed(reason), coordinator.uiState.value.updateInstall)
        }
        coordinator.startUpdate(hasUnsavedInput = false)
        runCurrent()

        assertEquals(UpdateInstallState.Ready(apk), coordinator.uiState.value.updateInstall)
        assertEquals(1, updates.checks)
    }

    @Test
    fun unsavedInputAndAnInProgressWorkoutRequireConfirmation() = runTest {
        val session = WorkoutSession(WorkoutSummary(0, "Strength", "A", null), emptyList())
        val workouts = FakeWorkoutsRepository(mapOf(1L to session))
        val updates = FakeUpdates()
        val coordinator = UpdateCoordinator(InstalledApp("1.0", 7), updates, workouts, this)
        coordinator.checkForUpdate()
        runCurrent()

        coordinator.startUpdate(hasUnsavedInput = true)
        runCurrent()
        assertEquals(UpdateInstallState.Warning, coordinator.uiState.value.updateInstall)
        assertEquals(0, updates.downloads)
        coordinator.dismissWarning()
        assertEquals(UpdateInstallState.Idle, coordinator.uiState.value.updateInstall)

        val workoutId = requireNotNull(workouts.startWorkout(1L))
        coordinator.startUpdate(hasUnsavedInput = false)
        runCurrent()
        assertEquals(UpdateInstallState.Warning, coordinator.uiState.value.updateInstall)
        assertEquals(0, updates.downloads)
        coordinator.confirmDownload()
        runCurrent()
        assertEquals(UpdateInstallState.Downloading(), coordinator.uiState.value.updateInstall)
        assertEquals(1, updates.downloads)
        coordinator.cancelDownload()

        check(workouts.finishWorkout(workoutId))
        coordinator.startUpdate(hasUnsavedInput = false)
        runCurrent()
        assertEquals(UpdateInstallState.Downloading(), coordinator.uiState.value.updateInstall)
        assertEquals(2, updates.downloads)
        coordinator.cancelDownload()

        check(workouts.deleteCompletedWorkout(workoutId))
        coordinator.startUpdate(hasUnsavedInput = false)
        runCurrent()
        assertEquals(UpdateInstallState.Downloading(), coordinator.uiState.value.updateInstall)
        assertEquals(3, updates.downloads)
        coordinator.cancelDownload()
    }

    private open class FakeUpdates(
        private val check: () -> UpdateCheckResult = {
            UpdateCheckResult.Available(
                FittyUpdate("1.0", 8, "https://github.com/delaynore/fitty/releases/download/test/fitty-8.apk"),
            )
        },
        private val downloadResult: suspend () -> UpdateDownloadResult = { awaitCancellation() },
    ) : UpdateService {
        var checks = 0
            private set
        var downloads = 0
            private set

        override suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult {
            checks++
            return check()
        }

        override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = Unit

        override suspend fun download(
            update: FittyUpdate,
            installedVersionCode: Long,
            onProgress: (Int) -> Unit,
        ): UpdateDownloadResult {
            downloads++
            return downloadResult()
        }

        override fun canRequestInstall() = error("The installer is invoked by the UI")
        override fun requestInstallPermission(launch: (Intent) -> Unit) = error("The installer is invoked by the UI")
        override fun openInstaller(apk: File, launch: (Intent) -> Unit) = error("The installer is invoked by the UI")
    }

    private class DelayedDownloads : FakeUpdates() {
        val firstProgress = CompletableDeferred<(Int) -> Unit>()
        val lateResult = CompletableDeferred<UpdateDownloadResult>()
        val retryResult = CompletableDeferred<UpdateDownloadResult>()

        override suspend fun download(
            update: FittyUpdate,
            installedVersionCode: Long,
            onProgress: (Int) -> Unit,
        ): UpdateDownloadResult {
            if (firstProgress.complete(onProgress)) {
                return try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    withContext(NonCancellable) { lateResult.await() }
                }
            }
            onProgress(25)
            return retryResult.await()
        }
    }
}
