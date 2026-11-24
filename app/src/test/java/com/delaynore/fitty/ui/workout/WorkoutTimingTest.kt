package com.delaynore.fitty.ui.workout

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.ui.SetInputField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutTimingTest {
    @Test
    fun restTimerStartsManuallyAndUsesElapsedTimeWhenTheWorkoutReopens() = runTest {
        val (workout, id) = openWorkout()
        val exercise = workout.exercise()
        workout.changeDraft(exercise, 1, SetInputField.Measure, "12")
        workout.logSet(id, exercise, 1)
        runCurrent()
        assertEquals(listOf(SetResult.Duration(12)), workout.exercise().sets.map { it.result })
        assertNull(workout.uiState.value.restDeadlineMillis)
        workout.startRestTimer()
        val deadline = requireNotNull(workout.uiState.value.restDeadlineMillis)
        assertEquals(120_000L, workout.remainingRestMillis(deadline))

        advanceTimeBy(45_000)
        workout.open(id)
        assertEquals(deadline, workout.uiState.value.restDeadlineMillis)
        assertEquals(75_000L, workout.remainingRestMillis(deadline))
        advanceTimeBy(75_000)
        workout.open(id)
        assertNull(workout.uiState.value.restDeadlineMillis)

        workout.startRestTimer()
        workout.resetRestTimer()
        workout.open(id)
        assertNull(workout.uiState.value.restDeadlineMillis)
    }

    @Test
    fun stoppingBeforeTheGoalRecordsElapsedSecondsAndCancelsTheScheduledCue() = runTest {
        val signals = RecordingSignals()
        val (workout, id) = openWorkout(goal = ExerciseGoal.Duration(40), signals = signals)
        workout.startSetTimer(id, 1, 1)
        val timer = requireNotNull(workout.uiState.value.setTimer)
        assertEquals(SetTimerSignal(timer.token, id, 40_000), signals.pending)
        advanceTimeBy(12_999)
        assertEquals(12_999L, workout.elapsedSetMillis(timer))
        workout.stopSetTimer(timer.token)
        runCurrent()

        assertNull(signals.pending)
        assertNull(workout.uiState.value.setTimer)
        assertEquals(listOf(SetResult.Duration(12)), workout.exercise().sets.map { it.result })
        workout.stopSetTimer(timer.token)
        runCurrent()
        assertEquals(1, workout.exercise().sets.size)
    }

    @Test
    fun stoppingInTheFirstSecondRecordsZeroWithoutSchedulingACue() = runTest {
        val signals = RecordingSignals()
        val (workout, id) = openWorkout(signals = signals)
        workout.startSetTimer(id, 1, 1)
        val timer = requireNotNull(workout.uiState.value.setTimer)
        assertNull(signals.pending)
        advanceTimeBy(999)
        workout.stopSetTimer(timer.token)
        runCurrent()

        assertNull(signals.pending)
        assertEquals(listOf(SetResult.Duration(0)), workout.exercise().sets.map { it.result })
    }

    @Test
    fun removedSetsCanBeRestoredInReverseOrderFromAnotherExercise() = runTest {
        val sets = listOf(LoggedSet(1, SetResult.Duration(12)), LoggedSet(2, SetResult.Duration(24)))
        val (workout, id) = openWorkout(sets = sets, secondExercise = true)
        workout.deleteRow(id, workout.exercise(), 2, 2, isRecorded = true)
        runCurrent()
        workout.deleteRow(id, workout.exercise(), 1, 1, isRecorded = true)
        runCurrent()
        assertEquals(emptyList<LoggedSet>(), workout.exercise().sets)

        workout.selectExercise(1)
        assertFalse(workout.canFinish())
        val (second, first) = workout.uiState.value.pendingRemovals
        workout.restoreRow(id, first)
        runCurrent()
        assertEquals(listOf(sets.first()), workout.exercise().sets)
        assertEquals(listOf(second), workout.uiState.value.pendingRemovals)
        workout.restoreRow(id, second)
        runCurrent()
        assertEquals(sets, workout.exercise().sets)
        assertEquals(emptyList<RemovedWorkoutRow>(), workout.uiState.value.pendingRemovals)
        assertTrue(workout.canFinish())

        workout.finish(id)
        runCurrent()
        assertTrue(workout.uiState.value.finished)
    }

    @Test
    fun theFirstUndoDeadlineExpiresWhileTheSecondSetCanStillBeRestored() = runTest {
        val first = LoggedSet(1, SetResult.Duration(12))
        val second = LoggedSet(2, SetResult.Duration(24))
        val (workout, id) = openWorkout(sets = listOf(first, second))
        workout.deleteRow(id, workout.exercise(), 1, 1, isRecorded = true)
        runCurrent()
        val firstRemoval = workout.uiState.value.pendingRemovals.single()
        advanceTimeBy(2_000)
        workout.deleteRow(id, workout.exercise(), 2, 2, isRecorded = true)
        runCurrent()
        val secondRemoval = workout.uiState.value.pendingRemovals.last()

        advanceTimeBy(2_999)
        assertEquals(listOf(firstRemoval, secondRemoval), workout.uiState.value.pendingRemovals)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(secondRemoval), workout.uiState.value.pendingRemovals)
        workout.restoreRow(id, firstRemoval)
        workout.restoreRow(id, secondRemoval)
        runCurrent()
        assertEquals(listOf(second), workout.exercise().sets)

        workout.deleteRow(id, workout.exercise(), 2, 2, isRecorded = true)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(emptyList<RemovedWorkoutRow>(), workout.uiState.value.pendingRemovals)
        assertEquals(emptyList<LoggedSet>(), workout.exercise().sets)
    }

    private suspend fun TestScope.openWorkout(
        goal: ExerciseGoal.Duration? = null,
        sets: List<LoggedSet> = emptyList(),
        signals: RecordingSignals = RecordingSignals(),
        secondExercise: Boolean = false,
    ): Pair<ActiveWorkoutState, Long> {
        val exercises = buildList {
            add(WorkoutExercise(1, "Plank", sets, type = ExerciseType.DURATION, goal = goal))
            if (secondExercise) add(WorkoutExercise(2, "Side plank", emptyList(), type = ExerciseType.DURATION))
        }
        val session = WorkoutSession(WorkoutSummary(0, "Strength", "A", null), exercises)
        val repository = FakeWorkoutsRepository(mapOf(1L to session))
        val id = requireNotNull(repository.startWorkout(1))
        val workout = ActiveWorkoutState(
            repository, -1, { testScheduler.currentTime }, signals, FakeExerciseCatalogRepository(), backgroundScope,
        )
        workout.open(id)
        runCurrent()
        return workout to id
    }

    private fun ActiveWorkoutState.exercise() = requireNotNull(uiState.value.session).exercises.first()

    // Mirrors the Finish button's enabled state in WorkoutScreen.
    private fun ActiveWorkoutState.canFinish() =
        requireNotNull(uiState.value.session).exercises.any { it.sets.isNotEmpty() }

    private class RecordingSignals : SetTimerSignalScheduler {
        var pending: SetTimerSignal? = null
            private set

        override fun schedule(token: String, workoutId: Long, deadlineMillis: Long): Boolean {
            pending = SetTimerSignal(token, workoutId, deadlineMillis)
            return true
        }

        override fun cancel(token: String) {
            if (pending?.token == token) pending = null
        }

        override fun cancelWorkout(workoutId: Long) {
            if (pending?.workoutId == workoutId) pending = null
        }
    }
}
