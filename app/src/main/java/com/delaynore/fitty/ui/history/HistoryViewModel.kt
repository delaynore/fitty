package com.delaynore.fitty.ui.history

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.SetInputErrors
import com.delaynore.fitty.ui.toInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class HistoryViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
    val history = workouts.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class Details(val workout: WorkoutSession?)

    fun workout(id: Long) = workouts.observeWorkout(id).map { session ->
        Details(session?.takeIf { it.summary.finishedAtMillis != null })
    }

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var setErrors by mutableStateOf<SetInputErrors?>(null)
        private set

    fun clearError() {
        error = null
        setErrors = null
    }

    fun changeSetInput(set: LoggedSet, weight: String, repetitions: String) {
        error = null
        if (setErrors != null) setErrors = set.toInput().copy(weight = weight, repetitions = repetitions).validate().errors
    }

    fun saveSet(sessionId: Long, set: LoggedSet, weight: String, repetitions: String, onSaved: () -> Unit) {
        val input = set.toInput().copy(weight = weight, repetitions = repetitions)
        val updated = input.toLoggedSet(set.position)
        if (updated == null) {
            setErrors = input.validate().errors
            return
        }
        changeHistory("Не удалось сохранить подход", onSaved) {
            workouts.updateCompletedSet(sessionId, set.id, updated.weightKg, updated.repetitions)
        }
    }

    fun deleteSet(sessionId: Long, setId: Long, onDeleted: () -> Unit) {
        changeHistory("Не удалось удалить подход", onDeleted) { workouts.deleteCompletedSet(sessionId, setId) }
    }

    fun deleteWorkout(sessionId: Long) {
        changeHistory("Не удалось удалить тренировку") { workouts.deleteCompletedWorkout(sessionId) }
    }

    private fun changeHistory(failureMessage: String, onSuccess: () -> Unit = {}, change: suspend () -> Boolean) {
        if (saving) return
        saving = true
        clearError()
        viewModelScope.launch {
            try {
                if (change()) onSuccess()
                else error = failureMessage
            } catch (_: SQLiteException) {
                error = failureMessage
            } finally {
                saving = false
            }
        }
    }
}
