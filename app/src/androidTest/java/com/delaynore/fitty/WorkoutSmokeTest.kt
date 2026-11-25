package com.delaynore.fitty

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = ApplicationProvider.getApplicationContext<FittyTestApplication>()

    @Before
    fun resetDatabase() {
        application.resetDatabase()
    }

    @SmokeTest
    @Test
    fun launchesWithCleanData() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitText("Создать программу")
            compose.onNodeWithText("Создать программу").assertIsDisplayed()
            compose.onNodeWithText("История").performClick()
            compose.awaitText("Завершённых тренировок пока нет")
        }
    }

    @SmokeTest
    @Test
    fun completesWorkoutOfflineAndFindsItsLoggedSetAfterReopening() {
        val wasInAirplaneMode = airplaneModeEnabled()
        val wifiWasEnabled = wifiEnabled()
        try {
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            compose.waitUntil(timeoutMillis = 5_000) { !wifiEnabled() }
            assertEquals(true, airplaneModeEnabled())

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.createAndStartWorkout()
                compose.onNodeWithText("Завершить").assertIsNotEnabled()
                compose.logSet(1, "60,5", "8")
                closeSoftKeyboard()
                compose.onNodeWithText("Завершить").performClick()
                compose.onNodeWithText("Завершить тренировку").performClick()
                compose.awaitText("60,5 кг × 8")
            }

            // Closing the Activity discards its ViewModels. Keep the Room file and reopen it.
            instrumentation.runOnMainSync { application.closeDatabase() }

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.awaitText("Силовая")
                compose.onNodeWithText("История").performClick()
                compose.awaitText("День А")
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("ru")))
                compose.onNodeWithText(date).assertIsDisplayed()
                compose.onNodeWithText("День А").performClick()
                compose.awaitText("60,5 кг × 8")
                compose.onNodeWithText("Жим штанги лёжа").assertIsDisplayed()
                compose.onNodeWithText("60,5 кг × 8").assertIsDisplayed()
            }
        } finally {
            val airplaneMode = if (wasInAirplaneMode) "enable" else "disable"
            shell("cmd connectivity airplane-mode $airplaneMode")
            val wifi = if (wifiWasEnabled) "enable" else "disable"
            shell("svc wifi $wifi")
            compose.waitUntil(timeoutMillis = 5_000) { wifiEnabled() == wifiWasEnabled }
            assertEquals(wasInAirplaneMode, airplaneModeEnabled())
        }
    }

    private fun airplaneModeEnabled() = shell("settings get global airplane_mode_on") == "1"

    private fun wifiEnabled() = shell("cmd wifi status").startsWith("Wifi is enabled")

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }
}
