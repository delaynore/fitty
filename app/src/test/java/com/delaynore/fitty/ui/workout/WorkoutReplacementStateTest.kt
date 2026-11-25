package com.delaynore.fitty.ui.workout

import com.delaynore.fitty.data.exercises.BuiltInExercises
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.domain.NoopWorkoutTimerStore
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.SetTimerSignalScheduler
import com.delaynore.fitty.ui.hasWeightField
import com.delaynore.fitty.ui.measureLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReplacementStateTest {
    @Test
    fun choosingADifferentTypeClearsTheDraftAndChangesTheFields() = runTest {
        val bench = WorkoutExercise(1, "Bench press", emptyList(), catalogExerciseId = "barbell-bench-press")
        val day = WorkoutSession(WorkoutSummary(0, "Strength", "Day A", null), listOf(bench))
        val workouts = FakeWorkoutsRepository(mapOf(1L to day))
        val catalog = FakeExerciseCatalogRepository().apply { allExercises.value = BuiltInExercises.all }
        val active = ActiveWorkoutState(
            workouts, -1, { testScheduler.currentTime }, NoTimerSignals, catalog, backgroundScope, NoopWorkoutTimerStore,
        )
        val id = requireNotNull(workouts.startWorkout(1L))
        active.open(id)
        runCurrent()
        active.changeDraft(bench, 1, SetInputField.Weight, "60")
        active.changeDraft(bench, 1, SetInputField.Measure, "8")
        assertEquals(SetInput(ExerciseType.WEIGHT_AND_REPS, "60", "8"), active.uiState.value.rows(bench.id).inputs[1])

        active.openReplacement()
        runCurrent()
        val recommendations = requireNotNull(active.uiState.value.replacement?.options)
        assertEquals(listOf("dumbbell-bench-press", "chest-press"), recommendations.similar.map { it.id })
        val pushup = recommendations.sameMuscle.first()
        assertEquals("push-up", pushup.id)

        active.replaceExercise(pushup)
        runCurrent()
        val state = active.uiState.value
        assertNull(state.replacement)
        assertTrue(state.rows(bench.id).inputs.isEmpty())
        val replacement = requireNotNull(state.session).exercises.single()
        assertEquals("push-up", replacement.catalogExerciseId)
        assertEquals(ExerciseType.REPS, replacement.type)
        assertFalse(replacement.type.hasWeightField)
        assertEquals("Повторения", replacement.type.measureLabel)
    }

    @Test
    fun replacingDurationExerciseClearsPendingRemovalsAndActiveSetTimer() = runTest {
        val plank = WorkoutExercise(1, "Планка", emptyList(), catalogExerciseId = "plank", type = ExerciseType.DURATION)
        val day = WorkoutSession(WorkoutSummary(0, "Core", "Day A", null), listOf(plank))
        val workouts = FakeWorkoutsRepository(mapOf(1L to day))
        val catalog = FakeExerciseCatalogRepository().apply { allExercises.value = BuiltInExercises.all }
        val active = ActiveWorkoutState(
            workouts, -1, { testScheduler.currentTime }, NoTimerSignals, catalog, backgroundScope, NoopWorkoutTimerStore,
        )
        val id = requireNotNull(workouts.startWorkout(1L))
        active.open(id)
        runCurrent()

        active.changeDraft(plank, 1, SetInputField.Measure, "45")
        active.deleteRow(id, plank, 1, 1, isRecorded = false)
        runCurrent()
        assertEquals(1, active.uiState.value.pendingRemovals.size)

        active.startSetTimer(id, plank.id, 2)
        assertTrue(active.uiState.value.setTimer != null)

        active.openReplacement()
        runCurrent()
        val replacement = requireNotNull(active.uiState.value.replacement?.options?.sameMuscle?.first())
        active.replaceExercise(replacement)
        runCurrent()

        assertNull(active.uiState.value.setTimer)
        assertTrue(active.uiState.value.pendingRemovals.isEmpty())
        assertTrue(active.uiState.value.rows(plank.id).inputs.isEmpty())
    }

    private object NoTimerSignals : SetTimerSignalScheduler {
        override fun schedule(token: String, workoutId: Long, deadlineMillis: Long) = true
        override fun cancel(token: String) = Unit
        override fun cancelWorkout(workoutId: Long) = Unit
    }
}
