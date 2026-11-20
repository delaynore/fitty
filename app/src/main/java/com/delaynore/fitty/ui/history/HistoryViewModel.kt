package com.delaynore.fitty.ui.history

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutsRepository
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

    fun clearError() {
        error = null
    }

    fun saveSet(sessionId: Long, setId: Long, weight: String, repetitions: String, onSaved: () -> Unit) {
        val kg = weight.trim().replace(',', '.').toDoubleOrNull()
        val reps = repetitions.trim().toIntOrNull()
        error = when {
            kg == null || !kg.isFinite() || kg < 0 -> "Укажи вес от 0 кг"
            reps == null || reps <= 0 -> "Укажи целое число повторений от 1"
            else -> null
        }
        if (kg != null && reps != null && error == null) {
            changeHistory("Не удалось сохранить подход", onSaved) {
                workouts.updateCompletedSet(sessionId, setId, kg, reps)
            }
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
