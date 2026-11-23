package com.delaynore.fitty.ui.programs

import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class ExerciseDraft(
    val id: Long,
    val name: String,
    val sets: String = "",
    val repetitions: String = "",
    val catalogExerciseId: String? = null,
    val type: ExerciseType = ExerciseType.WEIGHT_AND_REPS,
) {
    val valid: Boolean
        get() = name.isNotBlank() && sets.validRecommendation() && repetitions.validRecommendation()

    fun toExercise() = TrainingExercise(
        id = id,
        name = name,
        recommendedSets = sets.trim().toIntOrNull(),
        recommendedRepetitions = repetitions.trim().toIntOrNull(),
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

internal class ProgramEditorViewModel(private val programs: ProgramsRepository) : ViewModel() {
    private var nextDraftId = -1L
    private var programId = 0L
    private var initialName = ""
    private var initialDays = emptyList<TrainingDayDraft>()
    private var days by mutableStateOf(emptyList<TrainingDayDraft>())
    private var undoDeadlines by mutableStateOf(emptyMap<Long, Long>())
    private var editingDayId by mutableStateOf<Long?>(null)

    var editing by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var showDayErrors by mutableStateOf(false)
        private set

    val allPrograms = programs.allPrograms
    var selecting by mutableStateOf(false)
        private set
    var selectionError by mutableStateOf<String?>(null)
        private set

    val visibleDays get() = days.filterNot { it.id in undoDeadlines }
    val removedDays get() = undoDeadlines.keys.mapNotNull { id -> days.find { it.id == id } }
    val editingDay get() = days.find { it.id == editingDayId }
    val hasChanges get() = name != initialName || visibleDays != initialDays
    val editingExistingProgram get() = programId > 0

    fun openEditor(program: TrainingProgram? = null) {
        programId = program?.id ?: 0
        initialName = program?.name.orEmpty()
        initialDays = program?.days.orEmpty().map { day ->
            TrainingDayDraft(day.id, day.name, day.exercises.map { exercise ->
                ExerciseDraft(
                    exercise.id,
                    exercise.name,
                    exercise.recommendedSets?.toString().orEmpty(),
                    exercise.recommendedRepetitions?.toString().orEmpty(),
                    exercise.catalogExerciseId,
                    exercise.type,
                )
            })
        }
        name = initialName
        days = initialDays
        undoDeadlines = emptyMap()
        editingDayId = null
        error = null
        editing = true
    }

    fun closeEditor() {
        editing = false
        undoDeadlines = emptyMap()
    }

    fun observeProgram(id: Long) = programs.observeProgram(id)

    fun selectProgram(id: Long, onSelected: () -> Unit) {
        if (selecting) return
        selecting = true
        selectionError = null
        viewModelScope.launch {
            try {
                programs.selectProgram(id)
                onSelected()
            } catch (_: SQLiteException) {
                selectionError = "Не удалось выбрать программу"
            } finally {
                selecting = false
            }
        }
    }

    fun changeName(value: String) {
        name = value
        error = null
    }

    fun addDay() {
        val day = TrainingDayDraft(nextDraftId--, "", emptyList())
        days = days + day
        openDay(day.id)
    }

    fun openDay(id: Long) {
        editingDayId = id
        showDayErrors = false
        error = null
    }

    fun changeDayName(value: String) = updateDay { it.copy(name = value) }

    fun selectExercise(exercise: CatalogExercise, legacyId: Long?) = updateDay { day ->
        if (legacyId == null) {
            day.copy(exercises = day.exercises + ExerciseDraft(
                id = nextDraftId--,
                name = exercise.name,
                catalogExerciseId = exercise.id,
                type = exercise.type,
            ))
        } else {
            day.copy(exercises = day.exercises.map { draft ->
                if (draft.id != legacyId || draft.catalogExerciseId != null) draft else draft.copy(
                    name = exercise.name,
                    repetitions = if (draft.type == exercise.type) draft.repetitions else "",
                    catalogExerciseId = exercise.id,
                    type = exercise.type,
                )
            })
        }
    }

    fun deleteExercise(id: Long) = updateDay { day -> day.copy(exercises = day.exercises.filterNot { it.id == id }) }

    fun changeExercise(exercise: ExerciseDraft) = updateDay { day ->
        day.copy(exercises = day.exercises.map { if (it.id == exercise.id) exercise else it })
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
        if (editingDay?.valid == true) onFinished() else showDayErrors = true
    }

    private fun updateDay(update: (TrainingDayDraft) -> TrainingDayDraft) {
        days = days.map { if (it.id == editingDayId) update(it) else it }
    }

    fun removeDay(id: Long) {
        if (id in undoDeadlines) return
        val deadline = SystemClock.elapsedRealtime() + UNDO_MILLIS
        undoDeadlines = undoDeadlines + (id to deadline)
        viewModelScope.launch {
            delay(UNDO_MILLIS)
            if (undoDeadlines[id] == deadline) {
                days = days.filterNot { it.id == id }
                undoDeadlines = undoDeadlines - id
            }
        }
    }

    fun restoreDay(id: Long) {
        val deadline = undoDeadlines[id] ?: return
        if (SystemClock.elapsedRealtime() < deadline) undoDeadlines = undoDeadlines - id
    }

    fun saveProgram(onInvalidDay: () -> Unit, onSaved: () -> Unit) {
        if (saving) return
        val currentDays = visibleDays
        error = when {
            name.isBlank() -> "Введи название программы"
            currentDays.isEmpty() -> "Добавь хотя бы один тренировочный день"
            else -> null
        }
        if (error != null) return
        val invalidDay = currentDays.firstOrNull { !it.valid }
        if (invalidDay != null) {
            openDay(invalidDay.id)
            showDayErrors = true
            onInvalidDay()
            return
        }
        val program = TrainingProgram(programId, name, currentDays.map { it.toDay() })
        saving = true
        viewModelScope.launch {
            try {
                programs.saveProgram(program)
                closeEditor()
                onSaved()
            } catch (_: SQLiteException) {
                error = "Не удалось сохранить программу"
            } finally {
                saving = false
            }
        }
    }
}
