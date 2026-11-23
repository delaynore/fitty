package com.delaynore.fitty.ui.exercises

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import com.delaynore.fitty.data.exercises.ExerciseType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

internal data class ExerciseEditorUiState(
    val draft: ExerciseEditorState? = null,
    val savedExercise: CatalogExercise? = null,
    val draftVersion: Long = 0,
)

internal class ExerciseEditorViewModel(private val catalog: ExerciseCatalogRepository) : ViewModel() {
    private val state = MutableStateFlow(ExerciseEditorUiState())
    val uiState = state.asStateFlow()

    fun open(name: String) {
        openDraft(ExerciseEditorState(name))
    }

    fun open(exercise: CatalogExercise) {
        openDraft(ExerciseEditorState(exercise.name, exercise.type, details = exercise))
    }

    private fun openDraft(draft: ExerciseEditorState?) {
        state.update { ExerciseEditorUiState(draft = draft, draftVersion = it.draftVersion + 1) }
    }

    fun close() {
        openDraft(null)
    }

    fun changeName(name: String) {
        state.update { it.copy(draft = it.draft?.copy(name = name, error = null)) }
    }

    fun selectType(type: ExerciseType) {
        state.update { it.copy(draft = it.draft?.copy(type = type, error = null)) }
    }

    fun changeDetails(change: (CatalogExercise) -> CatalogExercise) {
        state.update { current ->
            val draft = current.draft ?: return@update current
            val details = draft.details ?: return@update current
            current.copy(draft = draft.copy(details = change(details), error = null))
        }
    }

    fun save() {
        val version = state.value.draftVersion
        val current = state.value.draft ?: return
        val type = current.type ?: return
        if (!current.canSave) return
        state.update { it.copy(draft = current.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val saved = current.details?.also { catalog.saveDetails(it) }
                    ?: catalog.create(current.name, type)
                state.update { if (it.draftVersion == version) it.copy(savedExercise = saved) else it }
            } catch (_: SQLiteException) {
                state.update {
                    if (it.draftVersion == version) {
                        it.copy(draft = it.draft?.copy(saving = false, error = "Не удалось сохранить упражнение"))
                    } else {
                        it
                    }
                }
            }
        }
    }
}
