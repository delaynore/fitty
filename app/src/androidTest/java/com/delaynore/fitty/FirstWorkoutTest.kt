package com.delaynore.fitty

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstWorkoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun firstWorkoutIsAvailableInHistoryAfterReopeningFitty() {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("ru")))

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("История")
            awaitText("Завершённых тренировок пока нет")
            tap("Главная")
            tap("Создать программу")
            enter("program-name", "Силовая")
            tap("Добавить день")
            enter("day-name", "День А")
            enter("exercise-name", "Жим лёжа")
            closeSoftKeyboard()
            tap("Сохранить день")
            awaitText("Сохранить программу")
            tap("Сохранить программу")

            awaitText("Начать тренировку")
            tap("Начать тренировку")
            awaitText("Завершить")
            compose.onNodeWithText("Завершить").assertIsNotEnabled()
            enter("workout-weight-1", "60,5")
            enter("workout-repetitions-1", "8")
            compose.onNodeWithContentDescription("Записать подход 1").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasTestTag("workout-logged-weight-1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60,5")
            compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertTextEquals("8")
            tap("Завершить")
            tap("Завершить тренировку")
            awaitText("60,5 кг × 8")
            compose.onNodeWithContentDescription("Назад").performClick()
            verifyCompletedWorkout(date)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Силовая")
            tap("История")
            verifyCompletedWorkout(date)
        }
    }

    private fun verifyCompletedWorkout(date: String) {
        awaitText(date)
        tap("День А")
        awaitText("Жим лёжа")
        awaitText("60,5 кг × 8")
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun enter(tag: String, text: String) = compose.onNodeWithTag(tag).performTextInput(text)

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
