package com.delaynore.fitty.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.InProgressWorkout
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HomeUiState(
    val loading: Boolean = true,
    val program: TrainingProgram? = null,
    val inProgressWorkout: InProgressWorkout? = null,
    val selectedDayId: Long? = null,
    val starting: Boolean = false,
    val error: String? = null,
    val confirmingCancelWorkoutId: Long? = null,
    val pendingEvent: HomeEvent? = null,
)

internal sealed interface HomeEvent { data class OpenWorkout(val id: Long) : HomeEvent }

internal class HomeViewModel(
    programs: ProgramsRepository,
    private val workouts: WorkoutsRepository,
    private val onWorkoutCancelled: (Long) -> Unit,
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(programs.homeProgram, workouts.inProgressWorkout, ::Pair).collect { (program, workout) ->
                state.update {
                    val selectedDayId = if (program?.id == it.program?.id) it.selectedDayId else null
                    it.copy(
                        loading = false,
                        program = program,
                        inProgressWorkout = workout,
                        selectedDayId = selectedDayId,
                    )
                }
            }
        }
    }

    fun selectDay(dayId: Long?) {
        state.update { it.copy(selectedDayId = dayId, error = null) }
    }

    fun startWorkout() {
        val current = state.value
        val program = current.program ?: return
        val dayId = program.days.firstOrNull { it.id == current.selectedDayId }?.id ?: program.days.firstOrNull()?.id ?: return
        changeWorkout("Не удалось начать тренировку") {
            val sessionId = workouts.startWorkout(dayId) ?: return@changeWorkout false
            state.update { it.copy(pendingEvent = HomeEvent.OpenWorkout(sessionId)) }
            true
        }
    }

    fun confirmCancelWorkout(id: Long) { state.update { it.copy(confirmingCancelWorkoutId = id, error = null) } }
    fun dismissCancelWorkout() { state.update { it.copy(confirmingCancelWorkoutId = null, error = null) } }

    fun cancelWorkout(id: Long) {
        changeWorkout("Не удалось отменить тренировку") {
            if (!workouts.cancelWorkout(id)) return@changeWorkout false
            onWorkoutCancelled(id)
            state.update { it.copy(confirmingCancelWorkoutId = null) }
            true
        }
    }

    fun consumeEvent(event: HomeEvent) {
        state.update { if (it.pendingEvent == event) it.copy(pendingEvent = null) else it }
    }

    private fun changeWorkout(failureMessage: String, change: suspend () -> Boolean) {
        viewModelScope.launchPersistenceSave(
            state = state,
            isBusy = { it.starting },
            onStart = { it.copy(starting = true, error = null) },
            onFailure = { it.copy(error = failureMessage) },
            onFinish = { it.copy(starting = false) },
            operation = change,
        )
    }
}
