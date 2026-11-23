package com.delaynore.fitty

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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSetTimerTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application get() = ApplicationProvider.getApplicationContext<FittyTestApplication>()
    private val cues = AtomicInteger()

    @Before
    fun resetDatabase() {
        application.resetDatabase()
        application.workoutTimeMillis = 10_000L
        application.setTimerCueCount = cues
    }

    @After
    fun resetClock() {
        application.workoutTimeMillis = null
        application.setTimerCueCount = null
    }

    @Test
    fun stoppingAfterReturningFromBackgroundLogsOneSetAndCanBeCorrectedInHistory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startPlankWorkout(goal = "40")
            tap("Старт")
            awaitText("Замер подхода")
            compose.onNodeWithContentDescription("Запустить таймер отдыха").assertExists()

            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 49_999L
            awaitMainThread()
            assertEquals(0, cues.get())
            application.workoutTimeMillis = 50_000L
            compose.waitUntil(timeoutMillis = 5_000) { cues.get() == 1 }
            application.workoutTimeMillis = 57_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("0:47")
            assertEquals(1, cues.get())
            compose.onNodeWithTag("workout-logged-duration-1", useUnmergedTree = true).assertDoesNotExist()
            tap("Стоп")
            awaitLogged(1, "47")
            compose.onNodeWithTag("workout-logged-duration-2", useUnmergedTree = true).assertDoesNotExist()

            compose.onNodeWithContentDescription("Запустить таймер отдыха").performClick()
            awaitText("2:00")
            tap("Завершить")
            tap("Завершить тренировку")
            awaitText("47 с")
            tap("47 с")
            compose.onNodeWithTag("history-set-duration").performTextReplacement("49")
            closeSoftKeyboard()
            tap("Сохранить")
            awaitText("49 с")
        }
    }

    @Test
    fun stoppingWithinTheFirstSecondLogsZeroAndCanBeSavedAgainInHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startPlankWorkout(goal = null)
            tap("Старт")
            awaitText("Стоп")
            application.workoutTimeMillis = 10_999L
            awaitText("0:00")
            tap("Стоп")
            awaitLogged(1, "0")
            tap("Завершить")
            tap("Завершить тренировку")
            awaitText("0 с")
            tap("0 с")
            compose.onNodeWithTag("history-set-duration").performTextReplacement("0")
            closeSoftKeyboard()
            tap("Сохранить")
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasTestTag("history-set-duration")).fetchSemanticsNodes().isEmpty()
            }
            awaitText("0 с")
        }
    }

    @Test
    fun stoppingBeforeTheGoalLogsElapsedTimeAndCancelsTheCue() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startPlankWorkout(goal = "40")
            tap("Старт")
            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 22_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("0:12")
            tap("Стоп")
            awaitLogged(1, "12")

            application.workoutTimeMillis = 51_000L
            awaitMainThread()
            assertEquals(0, cues.get())
        }
    }

    @Test
    fun stoppingWithoutAGoalLogsElapsedTimeAfterSavedStateRestoration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startPlankWorkout(goal = null)
            tap("Старт")
            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 25_000L
            scenario.onActivity { it.viewModelStore.clear() }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("0:15")
            tap("Стоп")
            awaitLogged(1, "15")
            assertEquals(0, cues.get())
        }
    }

    private fun startPlankWorkout(goal: String?) {
        awaitText("Создать программу")
        tap("Создать программу")
        compose.onNodeWithTag("program-name").performTextInput("Планка")
        tap("Добавить день")
        compose.onNodeWithTag("day-name").performTextInput("День А")
        compose.addCatalogExercise("Планка на предплечьях")
        if (goal != null) {
            tap("Планка на предплечьях")
            compose.onNodeWithTag("recommendation-duration").performScrollTo().performTextInput(goal)
            closeSoftKeyboard()
        }
        tap("Сохранить день")
        tap("Сохранить программу")
        awaitText("Начать тренировку")
        compose.onNodeWithText("Начать тренировку").performScrollTo().performClick()
        awaitText("Упражнение 1 из 1")
        awaitText("Планка на предплечьях")
    }

    private fun awaitLogged(number: Int, text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("workout-logged-duration-$number") and hasText(text), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun awaitMainThread() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
