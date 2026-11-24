package com.delaynore.fitty.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramSelection
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal data class ProgramsUiState(
    val programs: List<ProgramSelection>? = null,
    val selecting: Boolean = false,
    val selectionError: String? = null,
    val pendingEvent: ProgramsEvent? = null,
)
internal sealed interface ProgramsEvent { data object ProgramSelected : ProgramsEvent }

internal class ProgramsViewModel(private val programs: ProgramsRepository) : ViewModel() {
    private val localState = MutableStateFlow(ProgramsUiState())
    val uiState = combine(programs.allPrograms, localState) { list, local ->
        local.copy(programs = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUiState())

    fun selectProgram(id: Long) {
        viewModelScope.launchPersistenceSave(
            state = localState,
            isBusy = { it.selecting },
            onStart = { it.copy(selecting = true, selectionError = null) },
            onFailure = { it.copy(selectionError = "Не удалось выбрать программу") },
            onFinish = { it.copy(selecting = false) },
        ) {
            programs.selectProgram(id)
            localState.update { it.copy(pendingEvent = ProgramsEvent.ProgramSelected) }
            true
        }
    }

    fun consumeEvent(event: ProgramsEvent) {
        localState.update { if (it.pendingEvent == event) it.copy(pendingEvent = null) else it }
    }
}
