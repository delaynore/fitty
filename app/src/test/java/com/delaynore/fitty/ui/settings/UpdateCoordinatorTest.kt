package com.delaynore.fitty.ui.settings

import android.content.Intent
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
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

class UpdateCoordinatorTest {
    @get:Rule
    val folder = TemporaryFolder()

    @OptIn(ExperimentalCoroutinesApi::class)
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

    private class DelayedDownloads : UpdateService {
        val firstProgress = CompletableDeferred<(Int) -> Unit>()
        val lateResult = CompletableDeferred<UpdateDownloadResult>()
        val retryResult = CompletableDeferred<UpdateDownloadResult>()
        var checks = 0
            private set

        override suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult {
            checks++
            return UpdateCheckResult.Available(
                FittyUpdate("1.0", 8, "https://github.com/delaynore/fitty/releases/download/test/fitty-8.apk"),
            )
        }

        override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = Unit

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

        override fun canRequestInstall() = error("The installer is invoked by the UI")
        override fun requestInstallPermission(launch: (Intent) -> Unit) = error("The installer is invoked by the UI")
        override fun openInstaller(apk: File, launch: (Intent) -> Unit) = error("The installer is invoked by the UI")
    }
}
