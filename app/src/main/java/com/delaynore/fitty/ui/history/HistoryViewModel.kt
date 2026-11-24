package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class HistoryUiState(val history: List<WorkoutSummary>? = null)

internal class HistoryViewModel(workouts: WorkoutsRepository) : ViewModel() {
    val uiState = workouts.history.map(::HistoryUiState).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(),
    )
}
