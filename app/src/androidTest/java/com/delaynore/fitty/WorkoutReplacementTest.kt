package com.delaynore.fitty

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutReplacementTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun replacementKeepsItsPlaceAndGoalThroughResumeAndHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startBenchWorkout(secondExercise = "Подтягивания")
            compose.onNodeWithTag("workout-weight-1").performTextInput("60")
            closeSoftKeyboard()
            tap("Заменить упражнение")
            awaitText("Похожие движения")

            assertAbove("Похожие движения", "Жим гантелей лёжа")
            assertAbove("Жим гантелей лёжа", "Жим от груди в тренажёре")
            assertAbove("Жим от груди в тренажёре", "Та же основная группа мышц")
            assertAbove("Та же основная группа мышц", "Отжимания")
            assertEquals(1, compose.onAllNodes(inReplacementList("Жим гантелей лёжа")).fetchSemanticsNodes().size)
            compose.onNode(inReplacementList("Жим штанги лёжа")).assertDoesNotExist()
            chooseReplacement("Жим гантелей лёжа")
            awaitText("3 × 8")
            compose.onNode(hasTestTag("workout-weight-1") and hasText("60")).assertDoesNotExist()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Жим гантелей лёжа")
            awaitText("Упражнение 1 из 2")
            awaitText("3 × 8")
            compose.logSet(1, "22,5", "8")
            compose.onNodeWithText("Заменить упражнение").assertDoesNotExist()
            closeSoftKeyboard()
            compose.onNodeWithContentDescription("Следующее упражнение").performClick()
            awaitText("Подтягивания")
            compose.onNodeWithText("Заменить упражнение").assertExists()
            tap("Завершить")
            tap("Завершить тренировку")
            awaitText("Жим гантелей лёжа")
            awaitText("22,5 кг × 8")
            compose.onNodeWithContentDescription("Назад").performClick()
            tap("Главная")
            startWorkout()
            awaitText("Упражнение 1 из 2")
            awaitText("Жим штанги лёжа")
            awaitText("3 × 8")
        }
    }

    @Test
    fun changingTypeKeepsOnlyTheSetCountAndDeletingASetNeverUnlocksReplacement() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startBenchWorkout()
            removeFirstSet()
            awaitText("Пустая строка")
            tap("Заменить упражнение")
            awaitText("Та же основная группа мышц")
            chooseReplacement("Отжимания")

            awaitText("3 подх.")
            compose.onNodeWithText("3 × 8").assertDoesNotExist()
            compose.onNodeWithText("Пустая строка").assertDoesNotExist()
            compose.onNodeWithTag("workout-weight-1").assertDoesNotExist()
            compose.onNodeWithTag("workout-sets").performScrollToIndex(2)
            compose.onNodeWithTag("workout-set-row-3").assertExists()
            compose.onNodeWithTag("workout-sets").performScrollToIndex(0)
            compose.onNodeWithTag("workout-repetitions-1").performTextInput("6")
            compose.onNodeWithContentDescription("Записать подход 1").performClick()
            awaitLoggedRepetitions()
            compose.onNodeWithText("Заменить упражнение").assertDoesNotExist()

            closeSoftKeyboard()
            removeFirstSet()
            val undo = "Вернуть подход 1, Отжимания"
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasContentDescription(undo)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Заменить упражнение").assertDoesNotExist()
            compose.onNodeWithContentDescription(undo).performClick()
            awaitLoggedRepetitions()
            compose.onNodeWithText("Заменить упражнение").assertDoesNotExist()
            removeFirstSet()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasContentDescription(undo)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Отжимания")
            compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Заменить упражнение").assertDoesNotExist()
        }
    }

    private fun startBenchWorkout(secondExercise: String? = null) {
        awaitText("Создать программу")
        tap("Создать программу")
        compose.onNodeWithTag("program-name").performTextInput("Силовая")
        tap("Добавить день")
        compose.onNodeWithTag("day-name").performTextInput("День А")
        compose.addCatalogExercise("Жим штанги лёжа")
        secondExercise?.let(compose::addCatalogExercise)
        tap("Жим штанги лёжа")
        compose.onNodeWithTag("recommendation-sets").performScrollTo().performTextInput("3")
        compose.onNodeWithTag("recommendation-repetitions").performScrollTo().performTextInput("8")
        closeSoftKeyboard()
        tap("Сохранить день")
        tap("Сохранить программу")
        startWorkout()
        awaitText("3 × 8")
    }

    private fun startWorkout() {
        awaitText("Начать тренировку")
        compose.onNodeWithText("Начать тренировку").performScrollTo().performClick()
    }

    private fun chooseReplacement(name: String) {
        compose.onNode(inReplacementList(name)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Замена на сегодня")).fetchSemanticsNodes().isEmpty()
        }
        awaitText(name)
    }

    private fun inReplacementList(name: String) =
        hasText(name) and hasAnyAncestor(hasTestTag("workout-replacement-list"))

    private fun removeFirstSet() {
        compose.onNodeWithTag("workout-sets").performScrollToIndex(0)
        compose.onNodeWithTag("workout-set-row-1").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Удалить подход 1").performClick()
    }

    private fun awaitLoggedRepetitions() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("workout-logged-repetitions-1") and hasText("6"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertAbove(first: String, second: String) {
        val upper = compose.onNodeWithText(first).getUnclippedBoundsInRoot().top
        val lower = compose.onNodeWithText(second).getUnclippedBoundsInRoot().top
        assertTrue("$first should appear before $second", upper < lower)
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
