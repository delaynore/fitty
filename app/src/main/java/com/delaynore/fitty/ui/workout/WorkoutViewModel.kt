package com.delaynore.fitty.ui.workout

import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.SetInput
import com.delaynore.fitty.ui.toInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val UNDO_MILLIS = 5_000L
internal const val REST_DURATION_MILLIS = 120_000L
private const val REST_TIMER_KEY = "workoutRestTimer"

private class WorkoutRows {
    var addedThrough by mutableIntStateOf(0)
    val drafts = mutableStateMapOf<Int, SetInput>()
}

internal data class WorkoutRowDrafts(val addedThrough: Int = 0, val inputs: Map<Int, SetInput> = emptyMap())

internal data class WorkoutSetEdit(
    val exerciseId: Long,
    val exerciseName: String,
    val number: Int,
    val position: Int,
    val input: SetInput,
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

internal class WorkoutViewModel(
    private val workouts: WorkoutsRepository,
    private val savedState: SavedStateHandle,
    private val bootCount: Int,
    private val restTimerClock: () -> Long,
) : ViewModel() {
    private val sessionId = MutableStateFlow<Long?>(null)
    private val rowsByExercise = mutableStateMapOf<Long, WorkoutRows>()
    private val removedRows = mutableStateListOf<RemovedWorkoutRow>()
    val pendingRemovals: List<RemovedWorkoutRow> get() = removedRows.toList()

    @OptIn(ExperimentalCoroutinesApi::class)
    val session = sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else workouts.observeWorkout(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var exerciseIndex by mutableIntStateOf(0)
        private set
    var savedPosition by mutableStateOf<Int?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var editingSet by mutableStateOf<WorkoutSetEdit?>(null)
        private set
    var restDeadlineMillis by mutableStateOf<Long?>(null)
        private set

    fun open(id: Long) {
        if (id == sessionId.value) {
            restDeadlineMillis?.let(::expireRestTimer)
            return
        }
        rowsByExercise.clear()
        removedRows.clear()
        exerciseIndex = 0
        savedPosition = null
        error = null
        editingSet = null
        restoreRestTimer(id)
        sessionId.value = id
    }

    fun startRestTimer() {
        val id = sessionId.value ?: return
        val deadline = restTimerClock() + REST_DURATION_MILLIS
        restDeadlineMillis = deadline
        if (bootCount >= 0) savedState[REST_TIMER_KEY] = longArrayOf(id, deadline, bootCount.toLong())
    }

    fun resetRestTimer() {
        restDeadlineMillis = null
        savedState.remove<LongArray>(REST_TIMER_KEY)
    }

    fun expireRestTimer(deadline: Long) {
        if (restDeadlineMillis == deadline && remainingRestMillis(deadline) == 0L) resetRestTimer()
    }

    fun remainingRestMillis(deadline: Long): Long = (deadline - restTimerClock()).coerceAtLeast(0L)

    private fun restoreRestTimer(id: Long) {
        val timer = savedState.get<LongArray>(REST_TIMER_KEY)
        if (timer != null) {
            val (workoutId, deadline, savedBootCount) = timer
            if (workoutId == id && bootCount >= 0 && savedBootCount == bootCount.toLong() &&
                remainingRestMillis(deadline) > 0L
            ) {
                restDeadlineMillis = deadline
                return
            }
        }
        resetRestTimer()
    }

    fun selectExercise(index: Int) {
        exerciseIndex = index
        savedPosition = null
        error = null
    }

    fun rowDrafts(exerciseId: Long): WorkoutRowDrafts = rowsByExercise[exerciseId]?.let { rows ->
        WorkoutRowDrafts(rows.addedThrough, rows.drafts.toMap())
    } ?: WorkoutRowDrafts()

    fun changeDraft(exerciseId: Long, position: Int, input: SetInput) {
        rows(exerciseId).drafts[position] = if (input.errors == null) input else input.validate()
    }

    fun addSetRow(exerciseId: Long, lastPosition: Int) {
        if (!saving && lastPosition < Int.MAX_VALUE) rows(exerciseId).addedThrough = lastPosition + 1
    }

    private fun rows(exerciseId: Long): WorkoutRows = rowsByExercise.getOrPut(exerciseId, ::WorkoutRows)

    fun logSet(sessionId: Long, exerciseId: Long, position: Int) {
        val rows = rows(exerciseId)
        val input = rows.drafts[position] ?: SetInput()
        val set = input.toLoggedSet(position)
        if (set == null) {
            rows.drafts[position] = input.validate()
            return
        }
        save("Не удалось записать подход") {
            if (!workouts.logSet(sessionId, exerciseId, set)) return@save false
            rows.drafts.remove(position)
            savedPosition = position
            true
        }
    }

    fun clearFocusRequest() {
        savedPosition = null
    }

    fun deleteRow(sessionId: Long, exercise: WorkoutExercise, position: Int, number: Int, isRecorded: Boolean) {
        val rows = rows(exercise.id)
        save("Не удалось удалить подход") {
            val result = workouts.removeSetPosition(sessionId, exercise.id, position, isRecorded) ?: return@save false
            val removed = RemovedWorkoutRow(
                exercise.id, exercise.name, position, number, result.set, rows.drafts.remove(position),
                SystemClock.elapsedRealtime() + UNDO_MILLIS,
            )
            removedRows.add(removed)
            viewModelScope.launch {
                delay(UNDO_MILLIS)
                removedRows.remove(removed)
            }
            true
        }
    }

    fun restoreRow(sessionId: Long, removed: RemovedWorkoutRow) {
        if (removed !in removedRows || SystemClock.elapsedRealtime() >= removed.deadlineMillis) {
            removedRows.remove(removed)
            return
        }
        save("Не удалось вернуть подход") {
            if (!workouts.restoreSetPosition(sessionId, removed.exerciseId, removed.position, removed.set)) return@save false
            val rows = rows(removed.exerciseId)
            removed.draft?.let { rows.drafts[removed.position] = it }
            removedRows.remove(removed)
            true
        }
    }

    fun openSetEditor(exercise: WorkoutExercise, number: Int, set: LoggedSet) {
        error = null
        editingSet = WorkoutSetEdit(exercise.id, exercise.name, number, set.position, set.toInput())
    }

    fun changeSetEdit(input: SetInput) {
        editingSet = editingSet?.copy(input = if (input.errors == null) input else input.validate())
        error = null
    }

    fun closeSetEditor() {
        if (saving) return
        editingSet = null
        error = null
    }

    fun saveSetEdit(sessionId: Long) {
        val edit = editingSet ?: return
        val set = edit.input.toLoggedSet(edit.position)
        if (set == null) {
            editingSet = edit.copy(input = edit.input.validate())
            return
        }
        save("Не удалось сохранить подход") {
            if (!workouts.updateInProgressSet(sessionId, edit.exerciseId, set)) return@save false
            editingSet = null
            true
        }
    }

    fun finish(sessionId: Long, onFinished: () -> Unit) {
        save("Не удалось завершить тренировку") {
            if (!workouts.finishWorkout(sessionId)) return@save false
            resetRestTimer()
            onFinished()
            true
        }
    }

    private fun save(errorMessage: String, operation: suspend () -> Boolean) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                if (!operation()) error = errorMessage
            } catch (_: SQLiteException) {
                error = errorMessage
            } finally {
                saving = false
            }
        }
    }
}
