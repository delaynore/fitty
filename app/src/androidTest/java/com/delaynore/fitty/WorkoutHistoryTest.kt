package com.delaynore.fitty

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun editsToCompletedWorkoutSurviveReopeningFitty() {
        ActivityScenario.launch(MainActivity::class.java).use {
            createWorkout()
            logSet(1, "60", "6")
            tap("Завершить")
            awaitText("60 кг × 6")

            tap("60 кг × 6")
            awaitText("Изменить подход")
            compose.onNodeWithTag("history-set-weight").performTextReplacement("62,5")
            compose.onNodeWithTag("history-set-repetitions").performTextReplacement("7")
            tap("Сохранить")
            awaitText("62,5 кг × 7")
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            tap("История")
            awaitText("День А")
            tap("День А")
            awaitText("62,5 кг × 7")
        }
    }

    @Test
    fun deletingTheLastSetRequiresDeletingTheWorkout() {
        ActivityScenario.launch(MainActivity::class.java).use {
            createWorkout()
            logSet(1, "60", "6")
            logSet(2, "55", "8")
            tap("Завершить")
            awaitText("60 кг × 6")

            tap("60 кг × 6")
            awaitText("Изменить подход")
            compose.onNodeWithText("Удалить подход").performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("60 кг × 6")).fetchSemanticsNodes().isEmpty()
            }
            awaitText("55 кг × 8")

            compose.onNodeWithText("Удалить тренировку").performScrollTo().performClick()
            awaitText("Удалить тренировку?")
            tap("Отмена")

            compose.onNodeWithText("55 кг × 8").performScrollTo().performClick()
            awaitText("Изменить подход")
            compose.onNodeWithText("Удалить подход").assertDoesNotExist()
            compose.onNodeWithText("Удалить тренировку целиком").performScrollTo().performClick()
            awaitText("Удалить тренировку?")
            compose.onNode(hasAnyAncestor(isDialog()) and hasText("Удалить тренировку")).performClick()
            awaitText("Завершённых тренировок пока нет")
        }
    }

    private fun createWorkout() {
        awaitText("Создать программу")
        tap("Создать программу")
        enter("program-name", "Силовая")
        tap("Добавить день")
        enter("day-name", "День А")
        enter("exercise-name", "Жим лёжа")
        tap("Сохранить день")
        tap("Сохранить программу")
        awaitText("День А")
        tap("День А")
        tap("Начать тренировку")
        awaitText("Жим лёжа")
    }

    private fun logSet(position: Int, weight: String, repetitions: String) {
        enter("workout-weight-$position", weight)
        enter("workout-repetitions-$position", repetitions)
        compose.onNodeWithContentDescription("Записать подход $position").performClick()
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun enter(tag: String, text: String) =
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
