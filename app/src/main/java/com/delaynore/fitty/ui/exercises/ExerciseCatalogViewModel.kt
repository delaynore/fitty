package com.delaynore.fitty.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

internal class ExerciseCatalogViewModel(catalog: ExerciseCatalogRepository) : ViewModel() {
    val exercises = catalog.allExercises.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        initialValue = null,
    )
}
