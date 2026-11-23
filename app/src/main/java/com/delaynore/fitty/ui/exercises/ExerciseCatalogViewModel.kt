package com.delaynore.fitty.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class ExerciseCatalogUiState(val exercises: List<CatalogExercise>? = null)

internal class ExerciseCatalogViewModel(catalog: ExerciseCatalogRepository) : ViewModel() {
    val uiState = catalog.allExercises.map(::ExerciseCatalogUiState).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        initialValue = ExerciseCatalogUiState(),
    )
}
