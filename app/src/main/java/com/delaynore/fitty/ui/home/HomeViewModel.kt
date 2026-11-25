package com.delaynore.fitty.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.InProgressWorkout
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.consumePendingEvent
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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

private data class LocalHomeState(
    val ui: HomeUiState = HomeUiState(),
    val selectedProgramId: Long? = null,
)

internal sealed interface HomeEvent { data class OpenWorkout(val id: Long) : HomeEvent }

internal class HomeViewModel(
    programs: ProgramsRepository,
    private val workouts: WorkoutsRepository,
    private val onWorkoutCancelled: (Long) -> Unit,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalHomeState())
    val uiState = combine(programs.homeProgram, workouts.inProgressWorkout, localState) { program, workout, local ->
        local.ui.copy(
            loading = false,
            program = program,
            inProgressWorkout = workout,
            selectedDayId = local.ui.selectedDayId?.takeIf { local.selectedProgramId == program?.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectDay(dayId: Long?) {
        localState.update {
            it.copy(
                ui = it.ui.copy(selectedDayId = dayId, error = null),
                selectedProgramId = uiState.value.program?.id,
            )
        }
    }

    fun startWorkout() {
        val current = uiState.value
        val program = current.program ?: return
        val dayId = program.days.firstOrNull { it.id == current.selectedDayId }?.id ?: program.days.firstOrNull()?.id ?: return
        changeWorkout("Не удалось начать тренировку") {
            val sessionId = workouts.startWorkout(dayId) ?: return@changeWorkout false
            localState.update { it.copy(ui = it.ui.copy(pendingEvent = HomeEvent.OpenWorkout(sessionId))) }
            true
        }
    }

    fun confirmCancelWorkout(id: Long) {
        localState.update { it.copy(ui = it.ui.copy(confirmingCancelWorkoutId = id, error = null)) }
    }

    fun dismissCancelWorkout() {
        localState.update { it.copy(ui = it.ui.copy(confirmingCancelWorkoutId = null, error = null)) }
    }

    fun cancelWorkout(id: Long) {
        changeWorkout("Не удалось отменить тренировку") {
            if (!workouts.cancelWorkout(id)) return@changeWorkout false
            onWorkoutCancelled(id)
            localState.update { it.copy(ui = it.ui.copy(confirmingCancelWorkoutId = null)) }
            true
        }
    }

    fun consumeEvent(event: HomeEvent) {
        localState.update { current ->
            if (current.ui.pendingEvent == event) current.copy(ui = current.ui.copy(pendingEvent = null)) else current
        }
    }

    private fun changeWorkout(failureMessage: String, change: suspend () -> Boolean) {
        viewModelScope.launchPersistenceSave(
            state = localState,
            isBusy = { it.ui.starting },
            onStart = { it.copy(ui = it.ui.copy(starting = true, error = null)) },
            onFailure = { it.copy(ui = it.ui.copy(error = failureMessage)) },
            onFinish = { it.copy(ui = it.ui.copy(starting = false)) },
            operation = change,
        )
    }
}
