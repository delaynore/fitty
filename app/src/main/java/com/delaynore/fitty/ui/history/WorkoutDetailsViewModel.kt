package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.SetInput
import com.delaynore.fitty.ui.SetInputErrors
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class WorkoutDetails(val workout: WorkoutSession?)

internal data class WorkoutDetailsUiState(
    val workoutId: Long? = null,
    val details: WorkoutDetails? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val setErrors: SetInputErrors? = null,
    val editingSetId: Long? = null,
)

internal class WorkoutDetailsViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
    private val state = MutableStateFlow(WorkoutDetailsUiState())
    val uiState = state.asStateFlow()
    private var detailsJob: Job? = null

    fun showWorkout(id: Long?) {
        if (state.value.workoutId == id) return
        detailsJob?.cancel()
        state.update { it.copy(workoutId = id, details = null, error = null, setErrors = null, editingSetId = null) }
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

    fun openSetEditor(id: Long) { state.update { it.copy(editingSetId = id, error = null, setErrors = null) } }
    fun closeSetEditor() { state.update { it.copy(editingSetId = null, error = null, setErrors = null) } }

    fun changeSetInput(input: SetInput) {
        state.update { it.copy(error = null, setErrors = it.setErrors?.let { input.validate().errors }) }
    }

    fun saveSet(sessionId: Long, set: LoggedSet, input: SetInput) {
        val updated = input.toLoggedSet(set.position)
        if (updated == null) {
            state.update { it.copy(setErrors = input.validate().errors) }
            return
        }
        changeHistory("Не удалось сохранить подход") {
            workouts.updateCompletedSet(sessionId, set.id, updated.result)
        }
    }

    fun deleteSet(sessionId: Long, setId: Long) {
        changeHistory("Не удалось удалить подход") { workouts.deleteCompletedSet(sessionId, setId) }
    }

    fun deleteWorkout(sessionId: Long) {
        changeHistory("Не удалось удалить тренировку") { workouts.deleteCompletedWorkout(sessionId) }
    }

    private fun changeHistory(failureMessage: String, change: suspend () -> Boolean) {
        val workoutId = state.value.workoutId
        viewModelScope.launchPersistenceSave(
            state = state,
            isBusy = { it.saving },
            onStart = { it.copy(saving = true, error = null, setErrors = null) },
            onFailure = { if (it.workoutId == workoutId) it.copy(error = failureMessage) else it },
            onFinish = { it.copy(saving = false) },
        ) {
            change().also { success -> if (success) state.update { it.copy(editingSetId = null) } }
        }
    }
}
