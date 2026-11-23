package com.delaynore.fitty.ui.exercises

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import com.delaynore.fitty.data.exercises.ExerciseType
import kotlinx.coroutines.launch

internal data class ExerciseEditorState(
    val name: String,
    val type: ExerciseType? = null,
    val details: CatalogExercise? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && type != null && !saving
}

internal class ExerciseEditorViewModel(private val catalog: ExerciseCatalogRepository) : ViewModel() {
    var state by mutableStateOf<ExerciseEditorState?>(null)
        private set
    var savedExercise by mutableStateOf<CatalogExercise?>(null)
        private set

    fun open(name: String) {
        state = ExerciseEditorState(name)
    }

    fun open(exercise: CatalogExercise) {
        state = ExerciseEditorState(exercise.name, exercise.type, details = exercise)
    }

    fun close() {
        state = null
        savedExercise = null
    }

    fun changeName(name: String) {
        state = state?.copy(name = name, error = null)
    }

    fun selectType(type: ExerciseType) {
        state = state?.copy(type = type, error = null)
    }

    fun changeDetails(details: CatalogExercise) {
        state = state?.copy(details = details, error = null)
    }

    fun save() {
        val current = state ?: return
        val type = current.type ?: return
        if (!current.canSave) return
        state = current.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                savedExercise = current.details?.also { catalog.saveDetails(it) }
                    ?: catalog.create(current.name, type)
            } catch (_: SQLiteException) {
                state = current.copy(error = "Не удалось сохранить упражнение")
            }
        }
    }
}
