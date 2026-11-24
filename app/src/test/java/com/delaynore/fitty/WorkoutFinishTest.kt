package com.delaynore.fitty

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.FittyTheme
import com.delaynore.fitty.ui.workout.ActiveWorkoutState
import com.delaynore.fitty.ui.workout.SetTimerSignalScheduler
import com.delaynore.fitty.ui.workout.WorkoutScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutFinishTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun finishingAfterScreenRecreationOpensHistoryFromTheCurrentScreen() {
        val session = WorkoutSession(
            WorkoutSummary(0, "Strength", "A", null),
            listOf(WorkoutExercise(
                id = 1,
                name = "Squat",
                type = ExerciseType.REPS,
                sets = listOf(LoggedSet(1, SetResult.Reps(8))),
            )),
        )
        val workouts = FakeWorkoutsRepository(mapOf(1L to session))
        val id = requireNotNull(runBlocking { workouts.startWorkout(1L) })
        val finishStarted = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val delayedWorkouts = object : WorkoutsRepository by workouts {
            override suspend fun finishWorkout(sessionId: Long): Boolean {
                finishStarted.complete(Unit)
                allowFinish.await()
                return workouts.finishWorkout(sessionId)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val active = ActiveWorkoutState(
                delayedWorkouts, -1, { 0L }, NoTimerSignals, FakeExerciseCatalogRepository(), scope,
            )
            val screen = mutableIntStateOf(1)
            val destinations = mutableListOf<String>()
            compose.setContent {
                val generation = screen.intValue
                key(generation) {
                    FittyTheme {
                        WorkoutScreen(
                            id,
                            active,
                            onBack = { destinations += "Back from $generation" },
                            onFinished = { destinations += "History from $generation" },
                        )
                    }
                }
            }

            compose.onNodeWithText("Завершить").performClick()
            compose.onNodeWithText("Завершить тренировку").performClick()
            compose.runOnIdle {
                assertTrue(finishStarted.isCompleted)
                screen.intValue = 2
            }
            compose.runOnIdle { allowFinish.complete(Unit) }
            compose.runOnIdle { assertEquals(listOf("History from 2"), destinations) }
        } finally {
            scope.cancel()
        }
    }

    private object NoTimerSignals : SetTimerSignalScheduler {
        override fun schedule(token: String, workoutId: Long, deadlineMillis: Long) = true
        override fun cancel(token: String) = Unit
        override fun cancelWorkout(workoutId: Long) = Unit
    }
}
