package com.delaynore.fitty.ui.programs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun ProgramEditorScreen(viewModel: ProgramEditorViewModel, onBack: () -> Unit, onEditDay: () -> Unit, onSaved: () -> Unit) {
    val focus = LocalFocusManager.current
    ScreenColumn {
        PageHeader("Новая программа", onBack)
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = viewModel::changeName,
            label = { Text("Название программы") },
            singleLine = true,
            enabled = !viewModel.saving,
            modifier = Modifier.fillMaxWidth().testTag("program-name"),
        )
        Text("Тренировочные дни", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {
                focus.clearFocus()
                viewModel.editDay()
                onEditDay()
            },
            enabled = !viewModel.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(viewModel.savedDay?.name ?: "Добавить день") }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                focus.clearFocus()
                viewModel.saveProgram(onSaved)
            },
            enabled = viewModel.name.isNotBlank() && viewModel.savedDay != null && !viewModel.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Сохранить программу") }
    }
}

@Composable
internal fun TrainingDayEditorScreen(viewModel: ProgramEditorViewModel, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val day = viewModel.editingDay
    ScreenColumn {
        PageHeader("Тренировочный день", onBack)
        OutlinedTextField(
            value = day.name,
            onValueChange = viewModel::changeDayName,
            label = { Text("Название дня") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("day-name"),
        )
        OutlinedTextField(
            value = day.exerciseName,
            onValueChange = viewModel::changeExerciseName,
            label = { Text("Название упражнения") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("exercise-name"),
        )
        Button(
            onClick = {
                focus.clearFocus()
                viewModel.saveDay()
                onBack()
            },
            enabled = day.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Сохранить день") }
    }
}
