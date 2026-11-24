package com.delaynore.fitty

import android.content.Intent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.ui.settings.UpdateInstallState
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h960dp", application = SettingsTestApplication::class)
class SettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application: SettingsTestApplication
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun deleteDatabase() {
        application.deleteDatabase()
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
            val installedBuild = PackageInfoCompat.getLongVersionCode(installed)
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("О приложении").assertIsDisplayed()
            compose.onNodeWithText("Fitty").assertIsDisplayed()
            compose.onNodeWithText("Версия ${installed.versionName} · сборка $installedBuild").assertIsDisplayed()
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
            awaitText("Доступно обновление")
            compose.onNodeWithText("Доступно обновление").assertIsDisplayed()
            compose.onNodeWithText("Версия ${installed.versionName} · сборка $nextBuild").assertIsDisplayed()
        }
    }

    @Test
    fun aCompletedWorkoutDoesNotRequireAnUpdateWarning() {
        val updates = offerUpdate()
        createWorkout()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLoggedSet()
            compose.onNodeWithText("Продолжить тренировку").performClick()
            awaitText("Завершить")
            compose.onNodeWithText("Завершить").performClick()
            compose.onNodeWithText("Завершить тренировку").performClick()
            awaitText("60 кг × 6")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            compose.onNodeWithText("Продолжить обновление?").assertDoesNotExist()
            assertInstallerOpened(updates)
        }
    }

    @Test
    fun anInProgressWorkoutWarnsBeforeDownloadAndLetsTheLifterGoBackOrContinue() {
        val updates = offerUpdate()
        createWorkout()
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitLoggedSet()
            compose.onNodeWithText("Продолжить тренировку").performClick()
            awaitText("Завершить")
            compose.onNodeWithContentDescription("Назад").performClick()

            findUpdate()
            compose.onNodeWithText("Обновить").performClick()
            awaitText("Продолжить обновление?")
            compose.onNodeWithText("Продолжить обновление?").assertIsDisplayed()
            compose.onNodeWithText("Сохранённые программы и записанные подходы останутся. Несохранённый ввод может пропасть.")
                .assertIsDisplayed()
            compose.onNodeWithText("Назад").performClick()
            compose.runOnIdle { assertFalse(updates.downloadStarted.isCompleted) }

            compose.onNodeWithText("Обновить").performClick()
            awaitText("Продолжить обновление?")
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("Продолжить обновление?").assertDoesNotExist()
            assertInstallerOpened(updates)
        }
    }

    @Test
    fun unsavedProgramWarnsBeforeDownloadAndRemainsInTheEditor() {
        val updates = offerUpdate()
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            compose.onNodeWithText("Создать программу").performClick()
            compose.onNodeWithTag("program-name").performTextInput("Черновик программы")

            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithText("Проверить обновления").performClick()
            compose.onNodeWithText("Обновить").performClick()
            awaitText("Продолжить обновление?")
            compose.onNodeWithText("Продолжить обновление?").assertIsDisplayed()
            compose.onNodeWithText("Назад").performClick()
            compose.runOnIdle { assertFalse(updates.downloadStarted.isCompleted) }

            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithText("Черновик программы").assertIsDisplayed()

            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithText("Обновить").performClick()
            awaitText("Продолжить обновление?")
            compose.onNodeWithText("Продолжить").performClick()
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
            awaitText("Скачиваем обновление…")
            compose.onNodeWithText("Скачиваем обновление…").assertIsDisplayed()
            compose.onNodeWithText("История").performClick()
            compose.runOnIdle { download.complete(UpdateDownloadResult.Downloaded(updates.apk)) }
            awaitReadyToInstall(updates)
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
            awaitText("Скачиваем обновление…")
            compose.onNodeWithText("Скачиваем обновление…").assertIsDisplayed()

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity { download.complete(UpdateDownloadResult.Downloaded(updates.apk)) }
            awaitReadyToInstall(updates)
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
            compose.onNodeWithText("Отменить").performClick()
            compose.onNodeWithText("Скачиваем обновление…").assertDoesNotExist()
            compose.onNodeWithText("Обновить").assertIsEnabled()
        }
    }

    private fun createWorkout() = runBlocking {
        val container = application.appContainer(null)
        container.programs.saveProgram(TrainingProgram(0, "Силовая", listOf(
            TrainingDay(0, "День А", listOf(TrainingExercise(0, "Жим штанги лёжа", null, null))),
        )))
        val dayId = requireNotNull(container.programs.homeProgram.first()).days.single().id
        val workoutId = requireNotNull(container.workouts.startWorkout(dayId))
        val exercise = requireNotNull(container.workouts.observeWorkout(workoutId).first()).exercises.single()
        check(container.workouts.logSet(workoutId, exercise, LoggedSet(1, SetResult.WeightAndReps(60.0, 6))))
    }

    private fun awaitLoggedSet() {
        awaitText("Записано подходов: 1")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertProgress(progress: ProgressBarRangeInfo) {
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, progress)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(matcher).assertIsDisplayed()
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

    private fun awaitReadyToInstall(updates: FakeUpdates) {
        compose.waitUntil(timeoutMillis = 5_000) {
            application.appContainer(null).updates.uiState.value.updateInstall == UpdateInstallState.Ready(updates.apk)
        }
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

class SettingsTestApplication : FittyApplication() {
    override val databaseName = "settings-test.db"

    internal var updateServiceOverride: UpdateService? = null

    override val updateService: UpdateService
        get() = updateServiceOverride ?: super.updateService
}
