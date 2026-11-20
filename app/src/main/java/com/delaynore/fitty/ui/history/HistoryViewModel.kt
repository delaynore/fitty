package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class HistoryViewModel(private val workouts: WorkoutsRepository) : ViewModel() {
    val history = workouts.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun workout(id: Long) = workouts.observeWorkout(id).map { it?.takeIf { session -> session.summary.finishedAtMillis != null } }
}
