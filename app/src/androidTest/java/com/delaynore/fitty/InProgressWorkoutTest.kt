package com.delaynore.fitty

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InProgressWorkoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun recordedAndCorrectedSetSurvivesReopeningAndCanBeFinishedOffline() {
        val wasInAirplaneMode = shell("settings get global airplane_mode_on") == "1"
        if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode enable")
        try {
            check(shell("settings get global airplane_mode_on") == "1")
            ActivityScenario.launch(MainActivity::class.java).use {
                startWorkout()
                compose.onNodeWithText("Завершить").assertIsNotEnabled()
                enter("workout-weight-1", "60,5")
                enter("workout-repetitions-1", "6")
                compose.onNodeWithContentDescription("Записать подход 1").performClick()
                awaitTag("workout-logged-weight-1")
                tap("Назад", description = true)
                awaitText("Продолжить тренировку")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("Продолжить тренировку")
                tap("Продолжить тренировку")
                awaitTag("workout-logged-weight-1")
                compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60,5")
                tap("Изменить подход 1", description = true)
                awaitTag("workout-edit-weight")
                replace("workout-edit-weight", "62,5")
                replace("workout-edit-repetitions", "7")
                tap("Сохранить")
                awaitText("62,5")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("Продолжить тренировку")
                tap("Продолжить тренировку")
                awaitText("62,5")
                compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertTextEquals("7")
                tap("Завершить")
                tap("Завершить тренировку")
                awaitText("62,5 кг × 7")
                tap("Назад", description = true)
                tap("Главная")
                compose.onNodeWithText("Продолжить тренировку").assertDoesNotExist()
            }
        } finally {
            if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode disable")
        }
    }

    @Test
    fun cancelledWorkoutDisappearsAndItsTrainingDayCanBeStartedAgain() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startWorkout()
            enter("workout-weight-1", "60")
            enter("workout-repetitions-1", "6")
            tap("Записать подход 1", description = true)
            awaitTag("workout-logged-weight-1")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Записано подходов: 1")
            tap("Отменить тренировку")
            awaitText("Отменить тренировку?")
            tap("Оставить тренировку")
            tap("Отменить тренировку")
            tap("Отменить")
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Отменить тренировку")).fetchSemanticsNodes().isEmpty()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("День А")
            compose.onNodeWithText("Продолжить тренировку").assertDoesNotExist()
            tap("История")
            awaitText("Завершённых тренировок пока нет")
            tap("Главная")
            tap("День А")
            tap("Начать тренировку")
            awaitTag("workout-weight-1")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Завершить").assertIsNotEnabled()
        }
    }

    @Test
    fun twoRemovedSetsCanBeRestoredIndependentlyInReverseOrder() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startWorkout(secondExercise = "Тяга")
            logSet(1, "60", "6")
            logSet(2, "65", "5")
            closeSoftKeyboard()
            compose.onNodeWithTag("workout-sets").performScrollToIndex(0)

            removeSet(2)
            removeSet(1)
            awaitText("60 кг × 6")
            awaitText("65 кг × 5")
            tap("Следующее упражнение", description = true)
            awaitText("Тяга")
            compose.onNodeWithText("Завершить").assertIsNotEnabled()

            tap("Вернуть подход 1, Жим лёжа", description = true)
            tap("Вернуть подход 2, Жим лёжа", description = true)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Завершить") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Завершить").assertIsEnabled()
            tap("Все упражнения")
            awaitText("Записано подходов: 2")
            tap("1. Жим лёжа")
            awaitTag("workout-logged-weight-1")
            awaitTag("workout-logged-weight-2")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60")
            compose.onNodeWithTag("workout-logged-weight-2", useUnmergedTree = true).assertTextEquals("65")
        }
    }

    private fun logSet(position: Int, weight: String, repetitions: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("workout-weight-$position") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        enter("workout-weight-$position", weight)
        enter("workout-repetitions-$position", repetitions)
        tap("Записать подход $position", description = true)
        awaitTag("workout-logged-weight-$position")
    }

    private fun removeSet(position: Int) {
        compose.onNodeWithTag("workout-set-row-$position").performTouchInput { swipeRight() }
        tap("Удалить подход $position", description = true)
    }

    private fun startWorkout(secondExercise: String? = null) {
        awaitText("Создать программу")
        tap("Создать программу")
        enter("program-name", "Силовая")
        tap("Добавить день")
        enter("day-name", "День А")
        enter("exercise-name", "Жим лёжа")
        if (secondExercise != null) {
            closeSoftKeyboard()
            tap("Добавить упражнение")
            enter("exercise-name-2", secondExercise)
        }
        tap("Сохранить день")
        tap("Сохранить программу")
        awaitText("День А")
        tap("День А")
        tap("Начать тренировку")
        awaitTag("workout-weight-1")
    }

    private fun tap(text: String, description: Boolean = false) {
        val node = if (description) compose.onNodeWithContentDescription(text) else compose.onNodeWithText(text)
        node.performClick()
    }

    private fun enter(tag: String, text: String) = compose.onNodeWithTag(tag).performTextInput(text)

    private fun replace(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        enter(tag, text)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }
}
