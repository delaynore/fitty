package com.delaynore.fitty.ui.home

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class HomeState(val program: TrainingProgram?, val inProgressWorkoutId: Long?)

internal class HomeViewModel(programs: ProgramsRepository, private val workouts: WorkoutsRepository) : ViewModel() {
    val home = combine(programs.homeProgram, workouts.inProgressWorkoutId, ::HomeState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var selectedDayId by mutableStateOf<Long?>(null)
        private set
    var starting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun selectDay(dayId: Long?) {
        selectedDayId = dayId
        error = null
    }

    fun startWorkout(onStarted: (Long) -> Unit) {
        val program = home.value?.program ?: return
        val dayId = program.days.firstOrNull { it.id == selectedDayId }?.id ?: program.days.firstOrNull()?.id ?: return
        if (starting) return
        starting = true
        error = null
        viewModelScope.launch {
            try {
                val sessionId = workouts.startWorkout(dayId)
                if (sessionId == null) error = "Не удалось начать тренировку"
                else onStarted(sessionId)
            } catch (_: SQLiteException) {
                error = "Не удалось начать тренировку"
            } finally {
                starting = false
            }
        }
    }
}
