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
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.SavedRestTimer
import com.delaynore.fitty.domain.SavedSetTimer
import com.delaynore.fitty.domain.SetTimerSignal
import com.delaynore.fitty.domain.SetTimerSignalScheduler
import com.delaynore.fitty.domain.StorageException
import com.delaynore.fitty.domain.WorkoutTimerStore
import com.delaynore.fitty.domain.WorkoutTimersSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutTimingTest {
    @Test
    fun timersRestoreFromTheStoreAndFutureGoalIsScheduledAgain() = runTest {
        val store = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(1, 120_000, 7),
                SavedSetTimer("fixed", 1, 1, null, 1, 0, 40_000, null, 7),
            ),
        )
        val signals = RecordingSignals()
        val (workout, id) = openWorkout(
            goal = ExerciseGoal.Duration(40), signals = signals, bootCount = 7, timerStore = store,
        )

        assertEquals(1L, id)
        assertEquals(120_000L, workout.uiState.value.restDeadlineMillis)
        assertEquals("fixed", workout.uiState.value.setTimer?.token)
        assertEquals(SetTimerSignal("fixed", id, 40_000), signals.pending)
    }

    @Test
    fun invalidBootCountClearsStoredTimersImmediately() = runTest {
        val store = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(1, 120_000, 6),
                SavedSetTimer("fixed", 1, 1, null, 1, 0, null, null, 6),
            ),
        )
        val (workout, _) = openWorkout(bootCount = 7, timerStore = store)

        assertNull(workout.uiState.value.restDeadlineMillis)
        assertNull(workout.uiState.value.setTimer)
        assertEquals(WorkoutTimersSnapshot(), store.snapshot)
    }

    @Test
    fun rejectedGoalSignalKeepsTheTimerAndShowsAnError() = runTest {
        val signals = RecordingSignals(scheduleResult = false)
        val (workout, id) = openWorkout(goal = ExerciseGoal.Duration(40), signals = signals, bootCount = 7)

        workout.startSetTimer(id, 1, 1)

        assertEquals("Не удалось запустить сигнал цели", workout.uiState.value.error)
        assertEquals("fixed-token", workout.uiState.value.setTimer?.token)
    }

    @Test
    fun foregroundFailureIsShownWhileTheTimerKeepsRunning() = runTest {
        val signals = RecordingSignals()
        val (workout, id) = openWorkout(goal = ExerciseGoal.Duration(40), signals = signals, bootCount = 7)

        workout.startSetTimer(id, 1, 1)
        val timer = requireNotNull(workout.uiState.value.setTimer)
        signals.reportFailure(timer.token, id, requireNotNull(timer.goalAtMillis))
        runCurrent()

        assertEquals(timer, workout.uiState.value.setTimer)
        assertEquals("Не удалось запустить сигнал цели", workout.uiState.value.error)
    }

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
        assertEquals(WorkoutEvent.Finished(id), workout.uiState.value.pendingEvent)
        workout.consumeEvent(WorkoutEvent.Finished(id + 1))
        assertEquals(WorkoutEvent.Finished(id), workout.uiState.value.pendingEvent)
        workout.consumeEvent(WorkoutEvent.Finished(id))
        assertNull(workout.uiState.value.pendingEvent)
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

    @Test
    fun invalidOrForeignOrExpiredStoredTimersAreClearedOnOpenAndClearBeforeOpen() = runTest {
        val negativeBoot = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(1, 120_000, 7),
                SavedSetTimer("t1", 1, 1, null, 1, 0, null, null, 7),
            ),
        )
        val (wNeg, _) = openWorkout(bootCount = -1, timerStore = negativeBoot)
        assertNull(wNeg.uiState.value.restDeadlineMillis)
        assertNull(wNeg.uiState.value.setTimer)
        assertEquals(WorkoutTimersSnapshot(), negativeBoot.snapshot)

        val futureStart = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(1, 100, 7),
                SavedSetTimer("t2", 1, 1, null, 1, 50_000, null, null, 7),
            ),
        )
        advanceTimeBy(1_000)
        val (wFuture, _) = openWorkout(bootCount = 7, timerStore = futureStart)
        assertNull(wFuture.uiState.value.restDeadlineMillis)
        assertNull(wFuture.uiState.value.setTimer)
        assertEquals(WorkoutTimersSnapshot(), futureStart.snapshot)

        val foreignStore = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(999, 120_000, 7),
                SavedSetTimer("t3", 999, 1, null, 1, 0, null, null, 7),
            ),
        )
        val (wForeign, id) = openWorkout(bootCount = 7, timerStore = foreignStore)
        assertNull(wForeign.uiState.value.restDeadlineMillis)
        assertNull(wForeign.uiState.value.setTimer)
        assertEquals(WorkoutTimersSnapshot(), foreignStore.snapshot)

        val clearBeforeOpen = FakeTimerStore(
            WorkoutTimersSnapshot(
                SavedRestTimer(id, 120_000, 7),
                SavedSetTimer("t4", id, 1, null, 1, 0, null, null, 7),
            ),
        )
        val session = WorkoutSession(
            WorkoutSummary(0, "Strength", "A", null),
            listOf(WorkoutExercise(1, "Plank", emptyList(), type = ExerciseType.DURATION)),
        )
        val repo = FakeWorkoutsRepository(mapOf(1L to session))
        val active = ActiveWorkoutState(
            repo, 7, { testScheduler.currentTime }, RecordingSignals(),
            FakeExerciseCatalogRepository(), backgroundScope, clearBeforeOpen, { "fixed-token" },
        )
        active.clear(id)
        assertEquals(WorkoutTimersSnapshot(), clearBeforeOpen.snapshot)
    }

    @Test
    fun setTimerIsDiscardedWhenExerciseIsRemovedOrSetAlreadyLogged() = runTest {
        val logged = LoggedSet(1, SetResult.Duration(10))
        val storeLogged = FakeTimerStore(
            WorkoutTimersSnapshot(setTimer = SavedSetTimer("t1", 1, 1, null, 1, 0, 40_000, null, 7)),
        )
        val signalsLogged = RecordingSignals()
        val (wLogged, _) = openWorkout(sets = listOf(logged), signals = signalsLogged, bootCount = 7, timerStore = storeLogged)
        assertNull(wLogged.uiState.value.setTimer)
        assertNull(signalsLogged.pending)
        assertNull(storeLogged.snapshot.setTimer)

        val storeMissing = FakeTimerStore(
            WorkoutTimersSnapshot(setTimer = SavedSetTimer("t2", 1, 999, null, 1, 0, 40_000, null, 7)),
        )
        val signalsMissing = RecordingSignals()
        val (wMissing, _) = openWorkout(signals = signalsMissing, bootCount = 7, timerStore = storeMissing)
        assertNull(wMissing.uiState.value.setTimer)
        assertNull(signalsMissing.pending)
        assertNull(storeMissing.snapshot.setTimer)
    }

    @Test
    fun stoppingPreservesStoppedAtMillisAndAllowsRetryAfterStorageException() = runTest {
        val session = WorkoutSession(
            WorkoutSummary(0, "Strength", "A", null),
            listOf(WorkoutExercise(1, "Plank", emptyList(), type = ExerciseType.DURATION)),
        )
        val base = FakeWorkoutsRepository(mapOf(1L to session))
        var shouldFail = true
        val failingRepo = object : WorkoutsRepository by base {
            override suspend fun logSet(sessionId: Long, exercise: WorkoutExercise, set: LoggedSet): Boolean {
                if (shouldFail) throw StorageException(IllegalStateException("db error"))
                return base.logSet(sessionId, exercise, set)
            }
        }
        val id = requireNotNull(base.startWorkout(1))
        val store = FakeTimerStore()
        val active = ActiveWorkoutState(
            failingRepo, 7, { testScheduler.currentTime }, RecordingSignals(),
            FakeExerciseCatalogRepository(), backgroundScope, store, { "fixed-token" },
        )
        active.open(id)
        runCurrent()

        active.startSetTimer(id, 1, 1)
        advanceTimeBy(15_000)
        active.stopSetTimer("fixed-token")
        runCurrent()

        assertEquals("Не удалось записать подход", active.uiState.value.error)
        assertEquals(15_000L, active.uiState.value.setTimer?.stoppedAtMillis)
        assertEquals(15_000L, store.snapshot.setTimer?.stoppedAtMillis)

        shouldFail = false
        advanceTimeBy(5_000)
        active.stopSetTimer("fixed-token")
        runCurrent()

        assertNull(active.uiState.value.setTimer)
        assertNull(store.snapshot.setTimer)
        assertEquals(listOf(SetResult.Duration(15)), active.exercise().sets.map { it.result })
    }

    @Test
    fun deletingUnrecordedRowWithDraftRestoresDraftOnUndo() = runTest {
        val (workout, id) = openWorkout()
        val exercise = workout.exercise()
        workout.changeDraft(exercise, 1, SetInputField.Measure, "33")
        workout.deleteRow(id, exercise, 1, 1, isRecorded = false)
        runCurrent()

        assertNull(workout.uiState.value.rows(exercise.id).inputs[1])
        val removed = workout.uiState.value.pendingRemovals.single()
        workout.restoreRow(id, removed)
        runCurrent()

        assertEquals("33", workout.uiState.value.rows(exercise.id).inputs[1]?.measure)
        assertTrue(workout.uiState.value.pendingRemovals.isEmpty())
    }

    private suspend fun TestScope.openWorkout(
        goal: ExerciseGoal.Duration? = null,
        sets: List<LoggedSet> = emptyList(),
        signals: RecordingSignals = RecordingSignals(),
        secondExercise: Boolean = false,
        bootCount: Int = -1,
        timerStore: FakeTimerStore = FakeTimerStore(),
    ): Pair<ActiveWorkoutState, Long> {
        val exercises = buildList {
            add(WorkoutExercise(1, "Plank", sets, type = ExerciseType.DURATION, goal = goal))
            if (secondExercise) add(WorkoutExercise(2, "Side plank", emptyList(), type = ExerciseType.DURATION))
        }
        val session = WorkoutSession(WorkoutSummary(0, "Strength", "A", null), exercises)
        val repository = FakeWorkoutsRepository(mapOf(1L to session))
        val id = requireNotNull(repository.startWorkout(1))
        val workout = ActiveWorkoutState(
            repository, bootCount, { testScheduler.currentTime }, signals, FakeExerciseCatalogRepository(), backgroundScope,
            timerStore, { "fixed-token" },
        )
        workout.open(id)
        runCurrent()
        return workout to id
    }

    private fun ActiveWorkoutState.exercise() = requireNotNull(uiState.value.session).exercises.first()

    // Mirrors the Finish button's enabled state in WorkoutScreen.
    private fun ActiveWorkoutState.canFinish() =
        requireNotNull(uiState.value.session).exercises.any { it.sets.isNotEmpty() }

    private class RecordingSignals(private val scheduleResult: Boolean = true) : SetTimerSignalScheduler {
        override val failures = MutableSharedFlow<SetTimerSignal>(extraBufferCapacity = 1)
        var pending: SetTimerSignal? = null
            private set

        override fun schedule(token: String, workoutId: Long, deadlineMillis: Long): Boolean {
            pending = SetTimerSignal(token, workoutId, deadlineMillis)
            return scheduleResult
        }

        override fun cancel(token: String) {
            if (pending?.token == token) pending = null
        }

        override fun cancelWorkout(workoutId: Long) {
            if (pending?.workoutId == workoutId) pending = null
        }

        fun reportFailure(token: String, workoutId: Long, deadlineMillis: Long) {
            failures.tryEmit(SetTimerSignal(token, workoutId, deadlineMillis))
        }
    }

    private class FakeTimerStore(initial: WorkoutTimersSnapshot = WorkoutTimersSnapshot()) : WorkoutTimerStore {
        var snapshot = initial
            private set
        override fun read() = snapshot
        override fun save(snapshot: WorkoutTimersSnapshot) { this.snapshot = snapshot }
    }
}
