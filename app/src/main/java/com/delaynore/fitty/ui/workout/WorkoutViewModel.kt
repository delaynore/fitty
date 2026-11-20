package com.delaynore.fitty.ui.workout

import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.formatWeight
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val weightPattern = Regex("[0-9]+([.,][0-9]{1,4})?")
private val repetitionsPattern = Regex("[0-9]+")
private const val UNDO_MILLIS = 5_000L

internal data class SetInput(
    val weight: String = "",
    val repetitions: String = "",
    val validated: Boolean = false,
    private val originalWeight: String? = null,
) {
    val parsedWeight: Double?
        get() = weight.trim().takeIf { it == originalWeight || weightPattern.matches(it) }
            ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val parsedRepetitions: Int?
        get() = repetitions.trim().takeIf(repetitionsPattern::matches)?.toIntOrNull()?.takeIf { it > 0 }

    fun toLoggedSet(position: Int): LoggedSet? {
        val kg = parsedWeight ?: return null
        val reps = parsedRepetitions ?: return null
        return LoggedSet(position, kg, reps)
    }
}

internal class WorkoutRows {
    var addedThrough by mutableIntStateOf(0)
    val drafts = mutableStateMapOf<Int, SetInput>()
    val removedPositions = mutableStateMapOf<Int, Unit>()
}

internal data class RemovedWorkoutRow(
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val number: Int,
    val set: LoggedSet?,
    val draft: SetInput?,
    val deadlineMillis: Long,
)

internal class WorkoutViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
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

    fun open(id: Long) {
        if (id == sessionId.value) return
        rowsByExercise.clear()
        removedRows.clear()
        exerciseIndex = 0
        savedPosition = null
        error = null
        sessionId.value = id
    }

    fun selectExercise(index: Int) {
        exerciseIndex = index
        savedPosition = null
        error = null
    }

    fun rows(exerciseId: Long): WorkoutRows = rowsByExercise.getOrPut(exerciseId, ::WorkoutRows)

    fun logSet(sessionId: Long, exerciseId: Long, position: Int) {
        val rows = rows(exerciseId)
        val input = rows.drafts[position] ?: SetInput()
        val set = input.toLoggedSet(position)
        if (set == null) {
            rows.drafts[position] = input.copy(validated = true)
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
        if (position in rows.removedPositions) return
        save("Не удалось удалить подход") {
            val set = if (isRecorded) {
                workouts.deleteInProgressSet(sessionId, exercise.id, position) ?: return@save false
            } else {
                null
            }
            rows.removedPositions[position] = Unit
            val removed = RemovedWorkoutRow(
                exercise.id, exercise.name, position, number, set, rows.drafts.remove(position),
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
            val set = removed.set
            if (set != null && !workouts.logSet(sessionId, removed.exerciseId, set)) return@save false
            val rows = rows(removed.exerciseId)
            removed.draft?.let { rows.drafts[removed.position] = it }
            rows.removedPositions.remove(removed.position)
            removedRows.remove(removed)
            true
        }
    }

    fun editSet(sessionId: Long, exercise: WorkoutExercise, set: LoggedSet, onSaved: () -> Unit) {
        save("Не удалось сохранить подход") {
            if (!workouts.updateInProgressSet(sessionId, exercise.id, set)) return@save false
            onSaved()
            true
        }
    }

    fun finish(sessionId: Long, onFinished: () -> Unit) {
        save("Не удалось завершить тренировку") {
            if (!workouts.finishWorkout(sessionId)) return@save false
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

internal fun LoggedSet.toInput(): SetInput {
    val formattedWeight = formatWeight(weightKg)
    return SetInput(formattedWeight, repetitions.toString(), originalWeight = formattedWeight)
}
