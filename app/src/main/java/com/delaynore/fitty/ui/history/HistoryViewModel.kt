package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class HistoryUiState(val history: List<WorkoutSummary>? = null)

internal class HistoryViewModel(workouts: WorkoutsRepository) : ViewModel() {
    private val state = MutableStateFlow(HistoryUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            workouts.history.collect { history -> state.update { it.copy(history = history) } }
        }
    }
}
