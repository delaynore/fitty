package com.delaynore.fitty

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
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
        application.updateServiceOverride = UpdateService { installedVersionCode ->
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
        application.updateServiceOverride = UpdateService { results.next() }

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
        application.updateServiceOverride = UpdateService { UpdateCheckResult.NoStableRelease }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Настройки").performClick()
            compose.onNodeWithText("Проверить обновления").performClick()
            compose.onNodeWithText("Стабильных релизов пока нет.").assertIsDisplayed()
        }
    }
}
