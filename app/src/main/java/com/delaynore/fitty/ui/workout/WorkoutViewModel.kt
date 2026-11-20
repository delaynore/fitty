package com.delaynore.fitty.ui.workout

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class WorkoutViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
    private val sessionId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session = sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else workouts.observeWorkout(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var weight by mutableStateOf("")
        private set
    var repetitions by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val canLog: Boolean
        get() = parsedWeight() != null && parsedRepetitions() != null && !saving

    fun open(id: Long) {
        if (id == sessionId.value) return
        resetInput()
        error = null
        sessionId.value = id
    }

    fun changeWeight(value: String) {
        weight = value
    }

    fun changeRepetitions(value: String) {
        repetitions = value
    }

    fun logSet(sessionId: Long, exerciseId: Long) {
        val kg = parsedWeight() ?: return
        val reps = parsedRepetitions() ?: return
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                if (workouts.logSet(sessionId, exerciseId, kg, reps)) resetInput()
                else error = "Не удалось записать подход"
            } catch (_: SQLiteException) {
                error = "Не удалось записать подход"
            } finally {
                saving = false
            }
        }
    }

    fun finish(sessionId: Long, onFinished: () -> Unit) {
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                if (workouts.finishWorkout(sessionId)) onFinished()
                else error = "Запиши хотя бы один подход"
            } catch (_: SQLiteException) {
                error = "Не удалось завершить тренировку"
            } finally {
                saving = false
            }
        }
    }

    private fun resetInput() {
        weight = ""
        repetitions = ""
    }

    private fun parsedWeight() = weight.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    private fun parsedRepetitions() = repetitions.trim().toIntOrNull()?.takeIf { it > 0 }
}
