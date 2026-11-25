package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputErrors
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal data class WorkoutDetails(val workout: WorkoutSession?)

internal data class WorkoutDetailsUiState(
    val workoutId: Long? = null,
    val details: WorkoutDetails? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val setErrors: SetInputErrors? = null,
    val editingSetId: Long? = null,
)

internal class WorkoutDetailsViewModel(
    private val workoutId: Long,
    private val workouts: WorkoutsRepository,
) : ViewModel() {
    private val localState = MutableStateFlow(WorkoutDetailsUiState(workoutId = workoutId))
    val uiState = combine(workouts.observeWorkout(workoutId), localState) { session, local ->
        local.copy(details = WorkoutDetails(session?.takeIf { it.summary.finishedAtMillis != null }))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WorkoutDetailsUiState(workoutId = workoutId),
    )

    fun clearError() {
        localState.update { it.copy(error = null, setErrors = null) }
    }

    fun openSetEditor(id: Long) { localState.update { it.copy(editingSetId = id, error = null, setErrors = null) } }
    fun closeSetEditor() { localState.update { it.copy(editingSetId = null, error = null, setErrors = null) } }

    fun changeSetInput(input: SetInput) {
        localState.update { it.copy(error = null, setErrors = it.setErrors?.let { input.validate().errors }) }
    }

    fun saveSet(set: LoggedSet, input: SetInput) {
        val updated = input.toLoggedSet(set.position)
        if (updated == null) {
            localState.update { it.copy(setErrors = input.validate().errors) }
            return
        }
        changeHistory("Не удалось сохранить подход") {
            workouts.updateCompletedSet(workoutId, set.id, updated.result)
        }
    }

    fun deleteSet(setId: Long) {
        changeHistory("Не удалось удалить подход") { workouts.deleteCompletedSet(workoutId, setId) }
    }

    fun deleteWorkout() {
        changeHistory("Не удалось удалить тренировку") { workouts.deleteCompletedWorkout(workoutId) }
    }

    private fun changeHistory(failureMessage: String, change: suspend () -> Boolean) {
        viewModelScope.launchPersistenceSave(
            state = localState,
            isBusy = { it.saving },
            onStart = { it.copy(saving = true, error = null, setErrors = null) },
            onFailure = { it.copy(error = failureMessage) },
            onFinish = { it.copy(saving = false) },
        ) {
            change().also { success -> if (success) localState.update { it.copy(editingSetId = null) } }
        }
    }
}
