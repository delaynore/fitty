package com.delaynore.fitty

import android.content.Intent
import android.os.Build
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application: FittyTestApplication
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetDatabase() {
        application.resetDatabase()
    }

    @After
    fun resetUpdateService() {
        application.updateServiceOverride = null
    }

    @Test
    fun settingsShowsTheInstalledPackageVersion() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installedVersion = ""
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
                @Suppress("DEPRECATION")
                val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }
                installedVersion = "Версия ${info.versionName} · сборка $build"
            }
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("О приложении").assertIsDisplayed()
            compose.onNodeWithText("Fitty").assertIsDisplayed()
            compose.onNodeWithText(installedVersion).assertIsDisplayed()
        }
    }

    @Test
    fun checkingOnlyStartsOnRequestAndShowsTheNewBuildEvenWhenTheVersionNameMatches() {
        val checkStarted = CompletableDeferred<Long>()
        val result = CompletableDeferred<UpdateCheckResult>()
        application.updateServiceOverride = FakeUpdates { installedVersionCode ->
            checkStarted.complete(installedVersionCode)
            result.await()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            @Suppress("DEPRECATION")
            val installed = application.packageManager.getPackageInfo(application.packageName, 0)
            @Suppress("DEPRECATION")
            val installedBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installed.longVersionCode
            } else {
                installed.versionCode.toLong()
            }
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("О приложении").assertIsDisplayed()
            compose.runOnIdle { assertFalse(checkStarted.isCompleted) }

            compose.onNodeWithText("Проверить обновления").performClick()
            compose.onNodeWithText("Проверяем обновления…").assertIsDisplayed()
            compose.onNodeWithText("Проверить обновления").assertIsNotEnabled()

            assertEquals(installedBuild, runBlocking { withTimeout(5_000) { checkStarted.await() } })
            val nextBuild = installedBuild + 1
            result.complete(
                UpdateCheckResult.Available(
                    FittyUpdate(installed.versionName.orEmpty(), nextBuild, "https://github.com/delaynore/fitty/releases/download/test/fitty-$nextBuild.apk"),
                ),
            )
            compose.onNodeWithText("Доступно обновление").assertIsDisplayed()
            compose.onNodeWithText("Версия ${installed.versionName} · сборка $nextBuild").assertIsDisplayed()
        }
    }

    @Test
    fun failedCheckExplainsTheCauseAndCanBeRetried() {
        val failures = listOf(
            UpdateCheckFailure.Network to "Не удалось подключиться. Проверь интернет и попробуй ещё раз.",
            UpdateCheckFailure.GitHub to "Не удалось проверить обновления на GitHub. Попробуй ещё раз.",
            UpdateCheckFailure.MissingApk to "В стабильном релизе нет подходящего APK для Fitty. Попробуй ещё раз позже.",
        )
        val results = (failures.map { UpdateCheckResult.Failed(it.first) } + UpdateCheckResult.UpToDate).iterator()
        application.updateServiceOverride = FakeUpdates { results.next() }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Настройки").performClick()
            val check = compose.onNodeWithText("Проверить обновления")
            failures.forEach { (_, message) ->
                check.performClick()
                compose.onNodeWithText(message).assertIsDisplayed()
                check.assertIsEnabled()
            }
            check.performClick()
            compose.onNodeWithText("Текущая сборка актуальна.").assertIsDisplayed()
        }
    }

    @Test
    fun settingsReportsWhenThereAreNoStableReleases() {
        application.updateServiceOverride = FakeUpdates { UpdateCheckResult.NoStableRelease }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Проверить обновления").performClick()
            compose.onNodeWithText("Стабильных релизов пока нет.").assertIsDisplayed()
        }
    }

    @Test
    fun aCompletedWorkoutDoesNotRequireAnUpdateWarning() {
        val updates = offerUpdate()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout()
            compose.logSet(1, "60", "6")
            closeSoftKeyboard()
            compose.onNodeWithText("Завершить").performClick()
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("60 кг × 6")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Назад").performClick()

            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Продолжить обновление?").assertDoesNotExist()
            assertInstallerOpened(updates)
        }
    }

    @Test
    fun anInProgressWorkoutWarnsBeforeDownloadAndLetsTheLifterGoBackOrContinue() {
        val updates = offerUpdate()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout()
            compose.logSet(1, "60", "6")
            closeSoftKeyboard()
            compose.onNodeWithContentDescription("Назад").performClick()

            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Продолжить обновление?").assertIsDisplayed()
            compose.onNodeWithText("Сохранённые программы и записанные подходы останутся. Несохранённый ввод может пропасть.")
                .assertIsDisplayed()
            compose.onNodeWithText("Назад").performClick()
            compose.runOnIdle { assertFalse(updates.downloadStarted.isCompleted) }

            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Продолжить обновление?").assertDoesNotExist()
            assertInstallerOpened(updates)
        }
    }

    @Test
    fun aDownloadThatFinishesOnAnotherTabOnlyOpensTheInstallerOnceBackInSettings() {
        val download = CompletableDeferred<UpdateDownloadResult>()
        val updates = offerUpdate { download.await() }
        ActivityScenario.launch(MainActivity::class.java).use {
            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Скачиваем обновление…").assertIsDisplayed()
            compose.onNodeWithText("История").performClick()
            compose.runOnIdle { download.complete(UpdateDownloadResult.Downloaded(updates.apk)) }
            compose.runOnIdle { assertEquals(0, updates.installAttempts.get()) }

            compose.onNodeWithText("Настройки").performClick()
            assertInstallerOpened(updates)
            compose.onNodeWithText("Главная").performClick()
            compose.onNodeWithText("Настройки").performClick()
            compose.runOnIdle { assertEquals(1, updates.installAttempts.get()) }
        }
    }

    @Test
    fun aDownloadOnSettingsWaitsForTheAppToResumeBeforeOpeningTheInstaller() {
        val download = CompletableDeferred<UpdateDownloadResult>()
        val updates = offerUpdate { download.await() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Скачиваем обновление…").assertIsDisplayed()

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { download.complete(UpdateDownloadResult.Downloaded(updates.apk)) }
            compose.runOnIdle { assertEquals(0, updates.installAttempts.get()) }

            scenario.moveToState(Lifecycle.State.RESUMED)
            assertInstallerOpened(updates)
            compose.runOnIdle { assertEquals(1, updates.installAttempts.get()) }
        }
    }

    @Test
    fun downloadShowsProgressAsItBecomesAvailableAndWhenReturningToSettings() {
        val progress = CompletableDeferred<(Int) -> Unit>()
        offerUpdate { reportProgress ->
            progress.complete(reportProgress)
            awaitCancellation()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            assertProgress(ProgressBarRangeInfo.Indeterminate)
            val reportProgress = runBlocking { withTimeout(5_000) { progress.await() } }
            compose.runOnIdle { reportProgress(25) }
            assertProgress(ProgressBarRangeInfo(0.25f, 0f..1f))

            compose.onNodeWithText("История").performClick()
            compose.runOnIdle { reportProgress(70) }
            compose.onNodeWithText("Настройки").performClick()
            assertProgress(ProgressBarRangeInfo(0.7f, 0f..1f))
        }
    }

    @Test
    fun cancelStopsTheDownloadAndIgnoresItsLateProgressAndApkAfterRetry() {
        val cancelledApk = File.createTempFile("cancelled-update-", ".apk", application.cacheDir)
        val firstProgress = CompletableDeferred<(Int) -> Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val lateResult = CompletableDeferred<UpdateDownloadResult>()
        val secondDownload = CompletableDeferred<UpdateDownloadResult>()
        var attempts = 0
        val updates = offerUpdate { reportProgress ->
            if (++attempts == 1) {
                firstProgress.complete(reportProgress)
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    cancelled.complete(Unit)
                    withContext(NonCancellable) { lateResult.await() }
                }
            } else {
                reportProgress(25)
                secondDownload.await()
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                findUpdate()
                compose.onNodeWithText("Обновить").performClick()
                val reportLateProgress = runBlocking { withTimeout(5_000) { firstProgress.await() } }
                compose.onNodeWithText("Отменить").performClick()
                runBlocking { withTimeout(5_000) { cancelled.await() } }
                compose.onNodeWithText("Скачиваем обновление…").assertDoesNotExist()
                compose.onNodeWithText("Обновить").performClick()
                assertProgress(ProgressBarRangeInfo(0.25f, 0f..1f))

                compose.runOnIdle {
                    reportLateProgress(80)
                    lateResult.complete(UpdateDownloadResult.Downloaded(cancelledApk))
                }
                assertProgress(ProgressBarRangeInfo(0.25f, 0f..1f))
                compose.runOnIdle {
                    assertEquals(0, updates.installAttempts.get())
                    assertFalse(cancelledApk.exists())
                }

                secondDownload.complete(UpdateDownloadResult.Downloaded(updates.apk))
                assertInstallerOpened(updates)
            }
        } finally {
            lateResult.complete(UpdateDownloadResult.Downloaded(cancelledApk))
            cancelledApk.delete()
        }
    }

    @Test
    fun aFailedDownloadExplainsTheErrorAndCanBeRetriedWithoutAnotherUpdateCheck() {
        val retry = CompletableDeferred<UpdateDownloadResult>()
        val failures = listOf(
            UpdateDownloadFailure.Network to "Не удалось скачать обновление. Проверь интернет и попробуй ещё раз.",
            UpdateDownloadFailure.Delivery to "Не удалось получить APK целиком с GitHub. Попробуй ещё раз позже.",
            UpdateDownloadFailure.LocalWrite to "Не удалось сохранить APK. Освободи место на устройстве и попробуй ещё раз.",
            UpdateDownloadFailure.InvalidApk to "Скачанный APK повреждён или не соответствует релизу Fitty.",
        )
        val results = failures.iterator()
        val updates = offerUpdate {
            if (results.hasNext()) UpdateDownloadResult.Failed(results.next().first) else retry.await()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            failures.forEach { (_, message) ->
                compose.onNodeWithText(message).assertIsDisplayed()
                compose.runOnIdle { assertEquals(0, updates.installAttempts.get()) }
                compose.onNodeWithText("Повторить загрузку").performClick()
            }
            compose.onNodeWithText("Скачиваем обновление…").assertIsDisplayed()
            retry.complete(UpdateDownloadResult.Downloaded(updates.apk))
            assertInstallerOpened(updates)
        }
    }

    private fun assertProgress(progress: ProgressBarRangeInfo) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, progress)).assertIsDisplayed()
    }

    private fun offerUpdate(download: (suspend ((Int) -> Unit) -> UpdateDownloadResult)? = null) = FakeUpdates(download) { installedCode ->
        val nextCode = installedCode + 1
        UpdateCheckResult.Available(
            FittyUpdate("1.1", nextCode, "https://github.com/delaynore/fitty/releases/download/test/fitty-$nextCode.apk"),
        )
    }.also { application.updateServiceOverride = it }

    private fun findUpdate() {
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Проверить обновления").performClick()
    }

    private fun assertInstallerOpened(updates: FakeUpdates) {
        compose.waitUntil(timeoutMillis = 5_000) { updates.installerOpened.isCompleted }
        assertEquals(updates.apk, runBlocking { updates.installerOpened.await() })
    }
}

private class FakeUpdates(
    private val downloadResult: (suspend ((Int) -> Unit) -> UpdateDownloadResult)? = null,
    private val check: suspend (Long) -> UpdateCheckResult,
) : UpdateService {
    val apk = File("downloaded-update.apk")
    val downloadStarted = CompletableDeferred<FittyUpdate>()
    val installerOpened = CompletableDeferred<File>()
    val installAttempts = AtomicInteger()

    override suspend fun checkForUpdate(installedVersionCode: Long) = check(installedVersionCode)
    override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = Unit

    override suspend fun download(update: FittyUpdate, installedVersionCode: Long, onProgress: (Int) -> Unit): UpdateDownloadResult {
        downloadStarted.complete(update)
        return downloadResult?.invoke(onProgress) ?: UpdateDownloadResult.Downloaded(apk)
    }

    override fun canRequestInstall() = true
    override fun requestInstallPermission(launch: (Intent) -> Unit) = error("Installation permission is already granted")
    override fun openInstaller(apk: File, launch: (Intent) -> Unit): Boolean {
        installAttempts.incrementAndGet()
        installerOpened.complete(apk)
        return true
    }
}
