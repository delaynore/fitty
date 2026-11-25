package com.delaynore.fitty

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutHistoryNavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application get() = ApplicationProvider.getApplicationContext<FittyApplication>()

    @After
    fun deleteDatabase() {
        application.deleteDatabase()
    }

    @Test
    fun backFromCompletedWorkoutReturnsToHistory() {
        val id = createWorkout()
        runBlocking { assertTrue(application.appContainer(null).workouts.finishWorkout(id)) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("История").performClick()
            compose.awaitText("День А")
            compose.onNodeWithText("День А").performClick()
            compose.awaitText("60,5 кг × 8")
            compose.onNodeWithText("Жим штанги лёжа").assertIsDisplayed()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("60,5 кг × 8").assertDoesNotExist()
            compose.onNodeWithText("День А").assertIsDisplayed()
            compose.onNodeWithText("Силовая").assertIsDisplayed()
        }
    }

    @Test
    fun cancellingWorkoutRequiresConfirmationAndReturnsToTheTrainingDay() {
        createWorkout()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitText("Записано подходов: 1")
            compose.onNodeWithText("Отменить тренировку").performClick()
            compose.onNodeWithText("Отменить тренировку?").assertIsDisplayed()
            compose.onNodeWithText("Оставить тренировку").performClick()
            compose.onNodeWithText("Продолжить тренировку").assertIsDisplayed()

            compose.onNodeWithText("Отменить тренировку").performClick()
            compose.onNodeWithText("Отменить").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Начать тренировку") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Продолжить тренировку").assertDoesNotExist()
        }
    }

    private fun createWorkout(): Long = runBlocking {
        val container = application.appContainer(null)
        container.programs.saveProgram(TrainingProgram(0, "Силовая", listOf(
            TrainingDay(0, "День А", listOf(TrainingExercise(0, "Жим штанги лёжа", null, null))),
        )))
        val dayId = requireNotNull(container.programs.homeProgram.first()).days.single().id
        val workoutId = requireNotNull(container.workouts.startWorkout(dayId))
        val exercise = requireNotNull(container.workouts.observeWorkout(workoutId).first()).exercises.single()
        assertTrue(container.workouts.logSet(workoutId, exercise, LoggedSet(1, SetResult.WeightAndReps(60.5, 8))))
        workoutId
    }
}
