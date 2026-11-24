package com.delaynore.fitty.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingProgram
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProgramDetailsUiState(
    val detailsId: Long? = null,
    val details: TrainingProgram? = null,
    val detailsLoaded: Boolean = false,
)

internal class ProgramDetailsViewModel(private val programs: ProgramsRepository) : ViewModel() {
    private val state = MutableStateFlow(ProgramDetailsUiState())
    val uiState = state.asStateFlow()
    private var detailsJob: Job? = null

    fun showProgram(id: Long?) {
        if (state.value.detailsId == id) return
        detailsJob?.cancel()
        state.update { it.copy(detailsId = id, details = null, detailsLoaded = false) }
        detailsJob = id?.let {
            viewModelScope.launch {
                programs.observeProgram(id).collect { program ->
                    state.update { it.copy(details = program, detailsLoaded = true) }
                }
            }
        }
    }
}
