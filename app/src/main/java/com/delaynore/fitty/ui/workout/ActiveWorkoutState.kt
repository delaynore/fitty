package com.delaynore.fitty.ui.workout

import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.domain.ExerciseReplacements
import com.delaynore.fitty.domain.SavedRestTimer
import com.delaynore.fitty.domain.WorkoutTimerStore
import com.delaynore.fitty.domain.WorkoutTimersSnapshot
import com.delaynore.fitty.domain.replacementsFor
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.SetTimer
import com.delaynore.fitty.domain.SetTimerSignalScheduler
import com.delaynore.fitty.domain.restoreSetTimer
import com.delaynore.fitty.domain.saved
import com.delaynore.fitty.ui.launchPersistenceSave
import com.delaynore.fitty.domain.toInput
import com.delaynore.fitty.ui.consumePendingEvent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val UNDO_MILLIS = 5_000L
internal const val REST_DURATION_MILLIS = 120_000L

internal data class WorkoutRowDrafts(
    val addedThrough: Int = 0,
    val inputs: Map<Int, SetInput> = emptyMap(),
    val resetVersions: Map<Int, Long> = emptyMap(),
)

internal data class WorkoutSetEdit(
    val exerciseId: Long,
    val exerciseName: String,
    val number: Int,
    val position: Int,
    val input: SetInput,
    val version: Long,
)

internal data class RemovedWorkoutRow(
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val number: Int,
    val set: LoggedSet?,
    val draft: SetInput?,
    val deadlineMillis: Long,
)

internal data class WorkoutReplacement(
    val exerciseId: Long,
    val catalogId: String,
    val options: ExerciseReplacements? = null,
)

internal data class WorkoutUiState(
    val sessionId: Long? = null,
    val session: WorkoutSession? = null,
    val sessionLoaded: Boolean = false,
    val pendingEvent: WorkoutEvent? = null,
    val exerciseIndex: Int = 0,
    val rowsByExercise: Map<Long, WorkoutRowDrafts> = emptyMap(),
    val pendingRemovals: List<RemovedWorkoutRow> = emptyList(),
    val savedPosition: Int? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val editingSet: WorkoutSetEdit? = null,
    val restDeadlineMillis: Long? = null,
    val setTimer: SetTimer? = null,
    val replacement: WorkoutReplacement? = null,
) {
    fun rows(exerciseId: Long) = rowsByExercise[exerciseId] ?: WorkoutRowDrafts()
}

internal sealed interface WorkoutEvent { data class Finished(val sessionId: Long) : WorkoutEvent }

internal class ActiveWorkoutState(
    private val workouts: WorkoutsRepository,
    private val bootCount: Int,
    private val timerClock: () -> Long,
    private val setTimerSignals: SetTimerSignalScheduler,
    private val catalog: ExerciseCatalogRepository,
    private val scope: CoroutineScope,
    private val timerStore: WorkoutTimerStore,
    private val tokenGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private var timers = timerStore.read()
    private val state = MutableStateFlow(WorkoutUiState())
    val uiState = state.asStateFlow()
    private var sessionJob: Job? = null
    private var replacementJob: Job? = null
    private var nextEditVersion = 0L

    init {
        scope.launch {
            setTimerSignals.failures.collect { failed ->
                if (state.value.setTimer?.token == failed.token) {
                    state.update { it.copy(error = "Не удалось запустить сигнал цели") }
                }
            }
        }
    }

    fun open(id: Long) {
        if (id == state.value.sessionId) {
            state.value.restDeadlineMillis?.let(::expireRestTimer)
            return
        }
        sessionJob?.cancel()
        replacementJob?.cancel()
        discardSetTimer()
        val timer = restoreSetTimer(timers.setTimer, id, bootCount, timerClock())
        if (timer == null && timers.setTimer != null) saveTimers(timers.copy(setTimer = null))
        val restDeadline = restoreRestTimer(id)
        state.value = WorkoutUiState(sessionId = id, restDeadlineMillis = restDeadline, setTimer = timer)
        sessionJob = scope.launch {
            workouts.observeWorkout(id).collect { workout ->
                validateSetTimer(id, workout)
                showSession(workout)
            }
        }
    }

    private fun showSession(workout: WorkoutSession?) {
        val current = state.value
        val selected = workout?.exercises?.getOrNull(current.exerciseIndex)
        val replacement = current.replacement
        if (replacement != null && (
                selected?.id != replacement.exerciseId || selected.catalogExerciseId != replacement.catalogId ||
                    selected.hasEverLoggedSet || workout.summary.finishedAtMillis != null
            )
        ) closeReplacement()

        // A new exercise can reach the collector before replaceExercise returns.
        val previousExercises = current.session?.exercises.orEmpty().associateBy(WorkoutExercise::id)
        val replaced = workout?.exercises.orEmpty().mapNotNull { exercise ->
            val previous = previousExercises[exercise.id] ?: return@mapNotNull null
            exercise.id.takeIf { previous.catalogExerciseId != exercise.catalogExerciseId || previous.type != exercise.type }
        }.toSet()
        state.update {
            it.copy(
                session = workout,
                sessionLoaded = true,
                rowsByExercise = it.rowsByExercise - replaced,
                pendingRemovals = it.pendingRemovals.filterNot { row -> row.exerciseId in replaced },
            )
        }
    }

    fun startRestTimer() {
        val id = state.value.sessionId ?: return
        val deadline = timerClock() + REST_DURATION_MILLIS
        state.update { it.copy(restDeadlineMillis = deadline) }
        if (bootCount >= 0) saveTimers(timers.copy(restTimer = SavedRestTimer(id, deadline, bootCount)))
    }

    fun resetRestTimer() {
        state.update { it.copy(restDeadlineMillis = null) }
        saveTimers(timers.copy(restTimer = null))
    }

    fun expireRestTimer(deadline: Long) {
        if (state.value.restDeadlineMillis == deadline && remainingRestMillis(deadline) == 0L) resetRestTimer()
    }

    fun remainingRestMillis(deadline: Long): Long = (deadline - timerClock()).coerceAtLeast(0L)

    fun startSetTimer(workoutId: Long, exerciseId: Long, position: Int) {
        if (state.value.saving || state.value.setTimer != null) return
        val workout = state.value.session?.takeIf { it.summary.id == workoutId && it.summary.finishedAtMillis == null } ?: return
        val exercise = workout.exercises.getOrNull(state.value.exerciseIndex) ?: return
        if (exercise.id != exerciseId || exercise.type != ExerciseType.DURATION ||
            position in exercise.removedPositions || exercise.sets.any { it.position == position }
        ) return
        state.update { it.copy(error = null) }
        val startedAt = timerClock()
        val goalAt = (exercise.goal as? ExerciseGoal.Duration)?.let { startedAt + it.seconds * 1_000L }
        val timer = SetTimer(
            tokenGenerator(), workoutId, exerciseId, exercise.catalogExerciseId, position, startedAt, goalAt,
        )
        storeSetTimer(timer)
        scheduleFutureCue(timer)
    }

    fun elapsedSetMillis(timer: SetTimer): Long = timer.elapsedMillis(timerClock())

    fun stopSetTimer(token: String) {
        val timer = state.value.setTimer?.takeIf { it.token == token } ?: return
        if (state.value.saving) return
        val workout = state.value.session?.takeIf { it.summary.id == timer.workoutId && it.summary.finishedAtMillis == null }
        val exercise = workout?.exercises?.firstOrNull(timer::matches)
        if (exercise == null) {
            discardSetTimer()
            return
        }
        val stoppedAt = timer.stoppedAtMillis ?: timerClock()
        val stopped = timer.copy(stoppedAtMillis = stoppedAt)
        storeSetTimer(stopped)
        if (timer.goalAtMillis == null || stoppedAt < timer.goalAtMillis) setTimerSignals.cancel(token)
        val seconds = (stopped.elapsedMillis(stoppedAt) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val set = LoggedSet(timer.position, SetResult.Duration(seconds))
        save("Не удалось записать подход") {
            if (!persistSet(timer.workoutId, exercise, set)) return@save false
            if (state.value.setTimer?.token == token) clearSetTimer()
            true
        }
    }

    private fun storeSetTimer(timer: SetTimer) {
        state.update { it.copy(setTimer = timer) }
        if (bootCount >= 0) saveTimers(timers.copy(setTimer = timer.saved(bootCount)))
    }

    private fun validateSetTimer(id: Long, workout: WorkoutSession?) {
        val timer = state.value.setTimer?.takeIf { it.workoutId == id } ?: return
        val exercise = workout?.takeIf { it.summary.finishedAtMillis == null }?.exercises?.firstOrNull(timer::matches)
        if (exercise == null || timer.position in exercise.removedPositions) {
            discardSetTimer()
        } else if (exercise.sets.any { it.position == timer.position }) {
            if (timer.stoppedAtMillis == null) discardSetTimer() else clearSetTimer()
        } else {
            scheduleFutureCue(timer)
        }
    }

    private fun scheduleFutureCue(timer: SetTimer) {
        val deadline = timer.goalAtMillis ?: return
        if (timer.stoppedAtMillis != null || deadline <= timerClock()) return
        if (!setTimerSignals.schedule(timer.token, timer.workoutId, deadline)) {
            state.update { it.copy(error = "Не удалось запустить сигнал цели") }
        }
    }

    private fun discardSetTimer() {
        val timer = state.value.setTimer ?: return
        setTimerSignals.cancel(timer.token)
        clearSetTimer()
    }

    private fun clearSetTimer() {
        state.update { it.copy(setTimer = null) }
        saveTimers(timers.copy(setTimer = null))
    }

    private fun restoreRestTimer(id: Long): Long? {
        val timer = timers.restTimer
        if (timer != null) {
            if (timer.workoutId == id && bootCount >= 0 && timer.bootCount == bootCount &&
                remainingRestMillis(timer.deadlineMillis) > 0L
            ) return timer.deadlineMillis
        }
        if (timer != null) saveTimers(timers.copy(restTimer = null))
        return null
    }

    fun selectExercise(index: Int) {
        closeReplacement()
        state.update { it.copy(exerciseIndex = index, savedPosition = null, error = null) }
    }

    fun openReplacement() {
        val current = state.value
        val workout = current.session?.takeIf { it.summary.finishedAtMillis == null } ?: return
        val exercise = workout.exercises.getOrNull(current.exerciseIndex) ?: return
        val catalogId = exercise.catalogExerciseId ?: return
        if (current.saving || exercise.hasEverLoggedSet || current.replacement != null) return
        state.update { it.copy(replacement = WorkoutReplacement(exercise.id, catalogId), error = null) }
        replacementJob = scope.launch {
            catalog.allExercises.collect { exercises ->
                val options = exercises.replacementsFor(catalogId)
                state.update { it.copy(replacement = it.replacement?.copy(options = options)) }
            }
        }
    }

    fun closeReplacement() {
        replacementJob?.cancel()
        replacementJob = null
        state.update { it.copy(replacement = null) }
    }

    fun replaceExercise(replacement: CatalogExercise) {
        val sessionId = state.value.sessionId ?: return
        val choice = state.value.replacement ?: return
        save("Не удалось заменить упражнение") {
            if (!workouts.replaceExercise(sessionId, choice.exerciseId, choice.catalogId, replacement)) return@save false
            if (state.value.sessionId != sessionId) return@save true
            if (state.value.setTimer?.exerciseId == choice.exerciseId) discardSetTimer()
            closeReplacement()
            state.update {
                it.copy(
                    rowsByExercise = it.rowsByExercise - choice.exerciseId,
                    pendingRemovals = it.pendingRemovals.filterNot { row -> row.exerciseId == choice.exerciseId },
                    savedPosition = null,
                )
            }
            true
        }
    }

    fun changeDraft(exercise: WorkoutExercise, position: Int, field: SetInputField, text: String) {
        val current = state.value
        val active = current.session?.exercises?.getOrNull(current.exerciseIndex)
        if (current.saving || active?.id != exercise.id || active.catalogExerciseId != exercise.catalogExerciseId) return
        updateRows(exercise.id) { rows ->
            val input = rows.inputs[position] ?: SetInput(exercise.type)
            rows.copy(inputs = rows.inputs + (position to input.change(field, text)))
        }
    }

    fun addSetRow(exerciseId: Long, lastPosition: Int) {
        if (!state.value.saving && lastPosition < Int.MAX_VALUE) {
            updateRows(exerciseId) { it.copy(addedThrough = lastPosition + 1) }
        }
    }

    private fun updateRows(exerciseId: Long, change: (WorkoutRowDrafts) -> WorkoutRowDrafts) {
        state.update {
            it.copy(rowsByExercise = it.rowsByExercise + (exerciseId to change(it.rows(exerciseId))))
        }
    }

    fun logSet(sessionId: Long, exercise: WorkoutExercise, position: Int) {
        val current = state.value
        if (sessionId != current.sessionId || current.saving) return
        if (current.setTimer?.let { it.matches(exercise) && it.position == position } == true) return
        val input = current.rows(exercise.id).inputs[position] ?: SetInput(exercise.type)
        val set = input.toLoggedSet(position)
        if (set == null) {
            updateRows(exercise.id) { it.copy(inputs = it.inputs + (position to input.validate())) }
            return
        }
        save("Не удалось записать подход") {
            if (!persistSet(sessionId, exercise, set)) return@save false
            if (state.value.sessionId == sessionId) state.update { it.copy(savedPosition = set.position) }
            true
        }
    }

    private suspend fun persistSet(sessionId: Long, exercise: WorkoutExercise, set: LoggedSet): Boolean {
        if (!workouts.logSet(sessionId, exercise, set)) return false
        if (state.value.sessionId == sessionId) {
            updateRows(exercise.id) { it.copy(inputs = it.inputs - set.position) }
        }
        return true
    }

    fun clearFocusRequest() {
        state.update { it.copy(savedPosition = null) }
    }

    fun deleteRow(sessionId: Long, exercise: WorkoutExercise, position: Int, number: Int, isRecorded: Boolean) {
        save("Не удалось удалить подход") {
            val result = workouts.removeSetPosition(sessionId, exercise, position, isRecorded) ?: return@save false
            val current = state.value
            if (current.sessionId != sessionId) return@save true
            val rows = current.rows(exercise.id)
            val removed = RemovedWorkoutRow(
                exercise.id, exercise.name, position, number, result.set, rows.inputs[position],
                timerClock() + UNDO_MILLIS,
            )
            state.update {
                it.copy(
                    rowsByExercise = it.rowsByExercise + (exercise.id to rows.copy(inputs = rows.inputs - position)),
                    pendingRemovals = it.pendingRemovals + removed,
                )
            }
            scope.launch {
                delay(UNDO_MILLIS)
                state.update { it.copy(pendingRemovals = it.pendingRemovals - removed) }
            }
            true
        }
    }

    fun restoreRow(sessionId: Long, removed: RemovedWorkoutRow) {
        if (removed !in state.value.pendingRemovals || timerClock() >= removed.deadlineMillis) {
            state.update { it.copy(pendingRemovals = it.pendingRemovals - removed) }
            return
        }
        save("Не удалось вернуть подход") {
            if (!workouts.restoreSetPosition(sessionId, removed.exerciseId, removed.position, removed.set)) return@save false
            if (state.value.sessionId != sessionId) return@save true
            state.update {
                val draft = removed.draft
                val rows = it.rows(removed.exerciseId)
                it.copy(
                    rowsByExercise = if (draft == null) it.rowsByExercise else it.rowsByExercise + (
                        removed.exerciseId to rows.copy(
                            inputs = rows.inputs + (removed.position to draft),
                            resetVersions = rows.resetVersions + (
                                removed.position to ((rows.resetVersions[removed.position] ?: 0L) + 1)
                            ),
                        )
                    ),
                    pendingRemovals = it.pendingRemovals - removed,
                )
            }
            true
        }
    }

    fun openSetEditor(exercise: WorkoutExercise, number: Int, set: LoggedSet) {
        if (state.value.saving) return
        val edit = WorkoutSetEdit(exercise.id, exercise.name, number, set.position, set.result.toInput(), nextEditVersion++)
        state.update {
            it.copy(error = null, editingSet = edit)
        }
    }

    fun changeSetEdit(field: SetInputField, text: String) {
        if (state.value.saving) return
        state.update { current ->
            val edit = current.editingSet ?: return@update current
            current.copy(editingSet = edit.copy(input = edit.input.change(field, text)), error = null)
        }
    }

    fun closeSetEditor() {
        if (state.value.saving) return
        state.update { it.copy(editingSet = null, error = null) }
    }

    fun saveSetEdit(sessionId: Long) {
        val current = state.value
        if (current.sessionId != sessionId || current.saving) return
        val edit = current.editingSet ?: return
        val set = edit.input.toLoggedSet(edit.position)
        if (set == null) {
            state.update { it.copy(editingSet = edit.copy(input = edit.input.validate())) }
            return
        }
        save("Не удалось сохранить подход") {
            if (!workouts.updateInProgressSet(sessionId, edit.exerciseId, set)) return@save false
            if (state.value.sessionId == sessionId) state.update { it.copy(editingSet = null) }
            true
        }
    }

    fun finish(sessionId: Long) {
        save("Не удалось завершить тренировку") {
            if (!workouts.finishWorkout(sessionId)) return@save false
            clear(
                sessionId,
                WorkoutUiState(
                    sessionId = sessionId,
                    pendingEvent = WorkoutEvent.Finished(sessionId),
                ),
            )
            true
        }
    }

    fun consumeEvent(event: WorkoutEvent) {
        state.consumePendingEvent(event, WorkoutUiState::pendingEvent) { it.copy(pendingEvent = null) }
    }

    fun clear(sessionId: Long) = clear(sessionId, WorkoutUiState())

    private fun clear(sessionId: Long, nextState: WorkoutUiState) {
        setTimerSignals.cancelWorkout(sessionId)
        val snapshot = timers
        saveTimers(snapshot.copy(
            restTimer = snapshot.restTimer?.takeUnless { it.workoutId == sessionId },
            setTimer = snapshot.setTimer?.takeUnless { it.workoutId == sessionId },
        ))
        if (state.value.sessionId == sessionId) {
            sessionJob?.cancel()
            replacementJob?.cancel()
            state.value = nextState
        }
    }

    fun resetScreenState() {
        closeReplacement()
        state.update {
            it.copy(
                exerciseIndex = 0,
                rowsByExercise = emptyMap(),
                pendingRemovals = emptyList(),
                savedPosition = null,
                error = null,
                editingSet = null,
            )
        }
    }

    private fun saveTimers(snapshot: WorkoutTimersSnapshot) {
        timers = snapshot
        timerStore.save(snapshot)
    }

    private fun save(errorMessage: String, operation: suspend () -> Boolean) {
        val sessionId = state.value.sessionId
        scope.launchPersistenceSave(
            state = state,
            isBusy = { it.saving },
            onStart = { it.copy(saving = true, error = null) },
            onFailure = { if (it.sessionId == sessionId) it.copy(error = errorMessage) else it },
            onFinish = { if (it.sessionId == sessionId) it.copy(saving = false) else it },
            operation = operation,
        )
    }
}
