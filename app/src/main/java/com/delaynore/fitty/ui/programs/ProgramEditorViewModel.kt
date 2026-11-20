package com.delaynore.fitty.ui.programs

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.programs.ProgramsRepository
import kotlinx.coroutines.launch

internal data class TrainingDayDraft(val name: String = "", val exerciseName: String = "") {
    val canSave: Boolean get() = name.isNotBlank() && exerciseName.isNotBlank()
}

internal class ProgramEditorViewModel(private val programs: ProgramsRepository) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var savedDay by mutableStateOf<TrainingDayDraft?>(null)
        private set
    var editingDay by mutableStateOf(TrainingDayDraft())
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun changeName(value: String) {
        name = value
    }

    fun editDay() {
        editingDay = savedDay ?: TrainingDayDraft()
    }

    fun changeDayName(value: String) {
        editingDay = editingDay.copy(name = value)
    }

    fun changeExerciseName(value: String) {
        editingDay = editingDay.copy(exerciseName = value)
    }

    fun saveDay() {
        if (editingDay.canSave) savedDay = editingDay
    }

    fun reset() {
        name = ""
        savedDay = null
        editingDay = TrainingDayDraft()
        error = null
    }

    fun saveProgram(onSaved: () -> Unit) {
        val day = savedDay ?: return
        if (saving || name.isBlank()) return
        saving = true
        error = null
        val programName = name
        viewModelScope.launch {
            try {
                programs.createProgram(programName, day.name, day.exerciseName)
                reset()
                onSaved()
            } catch (_: SQLiteException) {
                error = "Не удалось сохранить программу"
            } finally {
                saving = false
            }
        }
    }
}
