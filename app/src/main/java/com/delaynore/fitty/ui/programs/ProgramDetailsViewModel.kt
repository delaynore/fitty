package com.delaynore.fitty.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingProgram
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class ProgramDetailsUiState(
    val detailsId: Long? = null,
    val details: TrainingProgram? = null,
    val detailsLoaded: Boolean = false,
)

internal class ProgramDetailsViewModel(programId: Long, programs: ProgramsRepository) : ViewModel() {
    val uiState = programs.observeProgram(programId).map { program ->
        ProgramDetailsUiState(detailsId = programId, details = program, detailsLoaded = true)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProgramDetailsUiState(detailsId = programId),
    )
}
