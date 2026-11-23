package com.delaynore.fitty

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypedWorkoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun theSamePlankUsesTheGoalOfTheSelectedDay() {
        ActivityScenario.launch(MainActivity::class.java).use {
            createProgram()
            addPlankDay("День А", "40")
            addPlankDay("День Б", "40", editedSeconds = "60")
            tap("Сохранить программу")

            awaitText("3 × 40 с")
            tap("Начать тренировку")
            awaitText("Упражнение 1 из 1")
            awaitText("3 × 40 с")
            compose.onNodeWithContentDescription("Назад").performClick()
            tap("Отменить тренировку")
            tap("Отменить")

            awaitText("Начать тренировку")
            tap("День Б")
            awaitText("3 × 60 с")
            tap("Начать тренировку")
            awaitText("Упражнение 1 из 1")
            awaitText("3 × 60 с")
        }
    }

    @Test
    fun resultsKeepTheirMeasureAfterWorkoutAndHistoryEdits() {
        ActivityScenario.launch(MainActivity::class.java).use {
            createProgram()
            tap("Добавить день")
            compose.onNodeWithTag("day-name").performTextInput("День А")
            listOf("Жим штанги лёжа", "Подтягивания", "Планка на предплечьях", "Бег", "Подтягивания с отягощением")
                .forEach(compose::addCatalogExercise)
            tap("Сохранить день")
            tap("Сохранить программу")
            awaitText("Начать тренировку")
            compose.onNodeWithText("Начать тренировку").performScrollTo().performClick()

            awaitText("Жим штанги лёжа")
            compose.logSet(1, "62,5", "8")
            nextExercise("Подтягивания")
            logSingleValue("repetitions", "5")
            nextExercise("Планка на предплечьях")
            logSingleValue("duration", "47")
            closeSoftKeyboard()
            compose.onNodeWithContentDescription("Изменить подход 1").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasTestTag("workout-edit-duration")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("workout-edit-duration").performTextReplacement("49")
            closeSoftKeyboard()
            tap("Сохранить")
            awaitLoggedValue("duration", "49")
            nextExercise("Бег")
            logSingleValue("distance", "1250,5")
            nextExercise("Подтягивания с отягощением")
            compose.logSet(1, "10", "4")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Жим штанги лёжа")
            nextExercise("Подтягивания")
            awaitLoggedValue("repetitions", "5")
            nextExercise("Планка на предплечьях")
            awaitLoggedValue("duration", "49")
            nextExercise("Бег")
            awaitLoggedValue("distance", "1250,5")
            nextExercise("Подтягивания с отягощением")
            tap("Завершить")
            tap("Завершить тренировку")

            editHistoryValue("62,5 кг × 8", "weight", "65", "65 кг × 8")
            editHistoryValue("5 повт.", "repetitions", "6", "6 повт.")
            editHistoryValue("49 с", "duration", "50", "50 с")
            editHistoryValue("1250,5 м", "distance", "1300,25", "1300,25 м")
            editHistoryValue("10 кг × 4", "weight", "12,5", "12,5 кг × 4")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("История")
            tap("История")
            awaitText("День А")
            tap("День А")
            listOf("65 кг × 8", "6 повт.", "50 с", "1300,25 м", "12,5 кг × 4")
                .forEach(::awaitText)
        }
    }

    private fun nextExercise(name: String) {
        closeSoftKeyboard()
        compose.onNodeWithContentDescription("Следующее упражнение").performClick()
        awaitText(name)
    }

    private fun logSingleValue(measure: String, value: String) {
        compose.onNodeWithTag("workout-weight-1").assertDoesNotExist()
        compose.onNodeWithTag("workout-$measure-1").performTextInput(value)
        compose.onNodeWithContentDescription("Записать подход 1").performClick()
        awaitLoggedValue(measure, value)
    }

    private fun awaitLoggedValue(measure: String, value: String) {
        val tag = "workout-logged-$measure-1"
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasText(value), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(value)
    }

    private fun editHistoryValue(value: String, measure: String, update: String, expected: String) {
        awaitText(value)
        compose.onNodeWithText(value).performScrollTo().performClick()
        compose.onNodeWithTag("history-set-$measure").performTextReplacement(update)
        closeSoftKeyboard()
        tap("Сохранить")
        awaitText(expected)
    }

    private fun createProgram() {
        awaitText("Создать программу")
        tap("Создать программу")
        compose.onNodeWithTag("program-name").performTextInput("Силовая")
    }

    private fun addPlankDay(name: String, seconds: String, editedSeconds: String? = null) {
        tap("Добавить день")
        compose.onNodeWithTag("day-name").performTextInput(name)
        compose.addCatalogExercise("Планка на предплечьях")
        tap("Планка на предплечьях")
        compose.onNodeWithTag("recommendation-sets").performScrollTo().performTextInput("3")
        val duration = compose.onNodeWithTag("recommendation-duration")
        duration.performScrollTo().performTextInput(seconds)
        if (editedSeconds != null) duration.performTextReplacement(editedSeconds)
        closeSoftKeyboard()
        tap("Сохранить день")
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
