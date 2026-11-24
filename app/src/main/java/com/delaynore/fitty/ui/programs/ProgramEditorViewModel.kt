package com.delaynore.fitty.ui.programs

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.ui.inputText
import com.delaynore.fitty.ui.launchPersistenceSave
import com.delaynore.fitty.ui.parseGoal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ExerciseDraft(
    val id: Long,
    val name: String,
    val sets: String = "",
    val target: String = "",
    val catalogExerciseId: String? = null,
    val type: ExerciseType = ExerciseType.WEIGHT_AND_REPS,
) {
    val valid: Boolean
        get() = name.isNotBlank() && sets.validRecommendation() && validTarget

    val goal get() = type.parseGoal(target)
    val validTarget get() = target.isBlank() || goal != null

    fun toExercise() = TrainingExercise(
        id = id,
        name = name,
        recommendedSets = sets.trim().toIntOrNull(),
        goal = goal,
        catalogExerciseId = catalogExerciseId,
        type = type,
    )
}

internal fun String.validRecommendation() = isBlank() || (trim().toIntOrNull() ?: 0) > 0

internal data class TrainingDayDraft(val id: Long, val name: String, val exercises: List<ExerciseDraft>) {
    val valid: Boolean get() = name.isNotBlank() && exercises.isNotEmpty() && exercises.all { it.valid }

    fun toDay() = TrainingDay(id, name, exercises.map { it.toExercise() })
}

private const val UNDO_MILLIS = 5_000L

internal data class ProgramEditorUiState(
    val draftVersion: Long = 0,
    val editing: Boolean = false,
    val programId: Long = 0,
    val name: String = "",
    val days: List<TrainingDayDraft> = emptyList(),
    val initialName: String = "",
    val initialDays: List<TrainingDayDraft> = emptyList(),
    val undoDeadlines: Map<Long, Long> = emptyMap(),
    val editingDayId: Long? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val showDayErrors: Boolean = false,
) {
    val visibleDays get() = days.filterNot { it.id in undoDeadlines }
    val removedDays get() = undoDeadlines.keys.mapNotNull { id -> days.find { it.id == id } }
    val editingDay get() = days.find { it.id == editingDayId }
    val hasChanges get() = name != initialName || visibleDays != initialDays
    val editingExistingProgram get() = programId > 0
}

internal class ProgramEditorViewModel(
    private val programs: ProgramsRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private val state = MutableStateFlow(ProgramEditorUiState())
    val uiState = state.asStateFlow()
    private var nextDraftId = -1L

    fun openEditor(program: TrainingProgram? = null) {
        val name = program?.name.orEmpty()
        val days = program?.days.orEmpty().map { day ->
            TrainingDayDraft(day.id, day.name, day.exercises.map { exercise ->
                ExerciseDraft(
                    exercise.id,
                    exercise.name,
                    exercise.recommendedSets?.toString().orEmpty(),
                    exercise.goal?.inputText().orEmpty(),
                    exercise.catalogExerciseId,
                    exercise.type,
                )
            })
        }
        state.update {
            it.copy(
                draftVersion = it.draftVersion + 1, editing = true, programId = program?.id ?: 0,
                name = name, days = days, initialName = name, initialDays = days,
                undoDeadlines = emptyMap(), editingDayId = null, error = null, showDayErrors = false,
            )
        }
    }

    fun closeEditor() {
        state.update { it.copy(editing = false, undoDeadlines = emptyMap()) }
    }

    fun changeName(value: String) {
        state.update { it.copy(name = value, error = null) }
    }

    fun addDay() {
        val day = TrainingDayDraft(nextDraftId--, "", emptyList())
        state.update { it.copy(days = it.days + day, editingDayId = day.id, showDayErrors = false, error = null) }
    }

    fun openDay(id: Long) {
        state.update { it.copy(editingDayId = id, showDayErrors = false, error = null) }
    }

    fun changeDayName(value: String) = updateDay { it.copy(name = value) }

    fun selectExercise(exercise: CatalogExercise, legacyId: Long?) {
        val newId = if (legacyId == null) nextDraftId-- else null
        updateDay { day ->
            if (newId != null) {
                day.copy(exercises = day.exercises + ExerciseDraft(
                    id = newId,
                    name = exercise.name,
                    catalogExerciseId = exercise.id,
                    type = exercise.type,
                ))
            } else {
                day.copy(exercises = day.exercises.map { draft ->
                    if (draft.id != legacyId || draft.catalogExerciseId != null) draft else draft.copy(
                        name = exercise.name,
                        target = if (draft.type == exercise.type) draft.target else "",
                        catalogExerciseId = exercise.id,
                        type = exercise.type,
                    )
                })
            }
        }
    }

    fun deleteExercise(id: Long) = updateDay { day -> day.copy(exercises = day.exercises.filterNot { it.id == id }) }

    fun changeExercise(id: Long, change: (ExerciseDraft) -> ExerciseDraft) = updateDay { day ->
        day.copy(exercises = day.exercises.map { if (it.id == id) change(it) else it })
    }

    fun moveExercise(id: Long, destination: Int) = updateDay { day ->
        val source = day.exercises.indexOfFirst { it.id == id }
        if (source == -1 || destination !in day.exercises.indices || source == destination) {
            day
        } else {
            val exercises = day.exercises.toMutableList()
            exercises.add(destination, exercises.removeAt(source))
            day.copy(exercises = exercises)
        }
    }

    fun finishDay(onFinished: () -> Unit) {
        if (state.value.editingDay?.valid == true) onFinished()
        else state.update { it.copy(showDayErrors = true) }
    }

    private fun updateDay(change: (TrainingDayDraft) -> TrainingDayDraft) {
        state.update { current ->
            current.copy(days = current.days.map { if (it.id == current.editingDayId) change(it) else it })
        }
    }

    fun removeDay(id: Long) {
        val current = state.value
        if (id in current.undoDeadlines || current.days.none { it.id == id }) return
        val deadline = clock() + UNDO_MILLIS
        state.update { it.copy(undoDeadlines = it.undoDeadlines + (id to deadline)) }
        viewModelScope.launch {
            delay(UNDO_MILLIS)
            state.update {
                if (it.draftVersion == current.draftVersion && it.undoDeadlines[id] == deadline) {
                    it.copy(days = it.days.filterNot { day -> day.id == id }, undoDeadlines = it.undoDeadlines - id)
                } else {
                    it
                }
            }
        }
    }

    fun restoreDay(id: Long) {
        val deadline = state.value.undoDeadlines[id] ?: return
        if (clock() < deadline) state.update { it.copy(undoDeadlines = it.undoDeadlines - id) }
    }

    fun saveProgram(onInvalidDay: () -> Unit, onSaved: () -> Unit) {
        val current = state.value
        if (current.saving) return
        val days = current.visibleDays
        val error = when {
            current.name.isBlank() -> "Введи название программы"
            days.isEmpty() -> "Добавь хотя бы один тренировочный день"
            else -> null
        }
        if (error != null) {
            state.update { it.copy(error = error) }
            return
        }
        val invalidDay = days.firstOrNull { !it.valid }
        if (invalidDay != null) {
            state.update { it.copy(editingDayId = invalidDay.id, showDayErrors = true, error = null) }
            onInvalidDay()
            return
        }
        val program = TrainingProgram(current.programId, current.name, days.map { it.toDay() })
        viewModelScope.launchPersistenceSave(
            state = state,
            isBusy = { it.saving },
            onStart = { it.copy(saving = true, error = null) },
            onFailure = { it.copy(error = "Не удалось сохранить программу") },
            onFinish = { it.copy(saving = false) },
        ) {
            programs.saveProgram(program)
            closeEditor()
            onSaved()
            true
        }
    }
}
