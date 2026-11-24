package com.delaynore.fitty.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramSelection
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.ui.launchPersistenceSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProgramsUiState(
    val programs: List<ProgramSelection>? = null,
    val selecting: Boolean = false,
    val selectionError: String? = null,
)

internal class ProgramsViewModel(private val programs: ProgramsRepository) : ViewModel() {
    private val state = MutableStateFlow(ProgramsUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            programs.allPrograms.collect { list -> state.update { it.copy(programs = list) } }
        }
    }

    fun selectProgram(id: Long, onSelected: () -> Unit) {
        viewModelScope.launchPersistenceSave(
            state = state,
            isBusy = { it.selecting },
            onStart = { it.copy(selecting = true, selectionError = null) },
            onFailure = { it.copy(selectionError = "Не удалось выбрать программу") },
            onFinish = { it.copy(selecting = false) },
        ) {
            programs.selectProgram(id)
            onSelected()
            true
        }
    }
}
