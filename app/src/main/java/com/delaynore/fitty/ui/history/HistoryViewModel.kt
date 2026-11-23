package com.delaynore.fitty.ui.history

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.SetInput
import com.delaynore.fitty.ui.SetInputErrors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HistoryUiState(
    val history: List<WorkoutSummary>? = null,
    val workoutId: Long? = null,
    val details: WorkoutDetails? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val setErrors: SetInputErrors? = null,
)

internal data class WorkoutDetails(val workout: WorkoutSession?)

internal class HistoryViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
    private val state = MutableStateFlow(HistoryUiState())
    val uiState = state.asStateFlow()
    private var detailsJob: Job? = null

    init {
        viewModelScope.launch {
            workouts.history.collect { history -> state.update { it.copy(history = history) } }
        }
    }

    fun showWorkout(id: Long?) {
        if (state.value.workoutId == id) return
        detailsJob?.cancel()
        state.update { it.copy(workoutId = id, details = null, error = null, setErrors = null) }
        detailsJob = id?.let {
            viewModelScope.launch {
                workouts.observeWorkout(id).collect { session ->
                    val details = WorkoutDetails(session?.takeIf { it.summary.finishedAtMillis != null })
                    state.update { it.copy(details = details) }
                }
            }
        }
    }

    fun clearError() {
        state.update { it.copy(error = null, setErrors = null) }
    }

    fun changeSetInput(input: SetInput) {
        state.update { it.copy(error = null, setErrors = it.setErrors?.let { input.validate().errors }) }
    }

    fun saveSet(sessionId: Long, set: LoggedSet, input: SetInput, onSaved: () -> Unit) {
        val updated = input.toLoggedSet(set.position)
        if (updated == null) {
            state.update { it.copy(setErrors = input.validate().errors) }
            return
        }
        changeHistory("Не удалось сохранить подход", onSaved) {
            workouts.updateCompletedSet(sessionId, set.id, updated.result)
        }
    }

    fun deleteSet(sessionId: Long, setId: Long, onDeleted: () -> Unit) {
        changeHistory("Не удалось удалить подход", onDeleted) { workouts.deleteCompletedSet(sessionId, setId) }
    }

    fun deleteWorkout(sessionId: Long) {
        changeHistory("Не удалось удалить тренировку") { workouts.deleteCompletedWorkout(sessionId) }
    }

    private fun changeHistory(failureMessage: String, onSuccess: () -> Unit = {}, change: suspend () -> Boolean) {
        if (state.value.saving) return
        val workoutId = state.value.workoutId
        state.update { it.copy(saving = true, error = null, setErrors = null) }
        viewModelScope.launch {
            try {
                if (change()) onSuccess()
                else state.update { if (it.workoutId == workoutId) it.copy(error = failureMessage) else it }
            } catch (_: SQLiteException) {
                state.update { if (it.workoutId == workoutId) it.copy(error = failureMessage) else it }
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }
}
