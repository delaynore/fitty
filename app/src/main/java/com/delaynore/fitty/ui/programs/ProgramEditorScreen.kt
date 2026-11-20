package com.delaynore.fitty.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.ui.DeleteIconButton
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.SwipeToRevealDelete

@Composable
internal fun ProgramEditorScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onEditDay: () -> Unit,
    onSaved: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxHeight()) {
        ScreenColumn(Modifier.weight(1f)) {
            PageHeader(if (viewModel.editingExistingProgram) "Редактирование программы" else "Новая программа", onBack)
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::changeName,
                label = { Text("Название программы") },
                singleLine = true,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().testTag("program-name"),
            )
            Text("Тренировочные дни", style = MaterialTheme.typography.titleMedium)
            viewModel.visibleDays.forEach { day ->
                key(day.id) {
                    val name = day.name.ifBlank { "День без названия" }
                    SwipeToRevealDelete(
                        description = "Удалить день «$name»",
                        modifier = Modifier.testTag("training-day-$name"),
                        onDelete = { if (!viewModel.saving) viewModel.removeDay(day.id) },
                    ) {
                        Surface(
                            onClick = {
                                focus.clearFocus()
                                viewModel.openDay(day.id)
                                onEditDay()
                            },
                            enabled = !viewModel.saving,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text("Упражнения: ${day.exercises.size}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    focus.clearFocus()
                    viewModel.addDay()
                    onEditDay()
                },
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Добавить день") }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (viewModel.removedDays.isNotEmpty()) {
                Column(
                    Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    viewModel.removedDays.forEach { day ->
                        key(day.id) {
                            val name = day.name.ifBlank { "День без названия" }
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Удалён день «$name»", Modifier.weight(1f))
                                    TextButton(
                                        onClick = { viewModel.restoreDay(day.id) },
                                        enabled = !viewModel.saving,
                                        modifier = Modifier.semantics { contentDescription = "Вернуть день «$name»" },
                                    ) { Text("Вернуть") }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    focus.clearFocus()
                    viewModel.saveProgram(onEditDay, onSaved)
                },
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Сохранить программу") }
        }
    }
}

@Composable
internal fun TrainingDayEditorScreen(viewModel: ProgramEditorViewModel, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    val day = viewModel.editingDay ?: return
    val showErrors = viewModel.showDayErrors
    Column(Modifier.fillMaxHeight()) {
        ScreenColumn(Modifier.weight(1f)) {
            PageHeader("Тренировочный день", onBack)
            OutlinedTextField(
                value = day.name,
                onValueChange = viewModel::changeDayName,
                label = { Text("Название дня") },
                singleLine = true,
                isError = showErrors && day.name.isBlank(),
                supportingText = if (showErrors && day.name.isBlank()) ({ Text("Введи название дня") }) else null,
                modifier = Modifier.fillMaxWidth().testTag("day-name"),
            )
            day.exercises.forEachIndexed { index, exercise ->
                key(exercise.id) {
                    ExerciseEditor(
                        exercise = exercise,
                        index = index,
                        showErrors = showErrors,
                        onChange = viewModel::changeExercise,
                        onDelete = { viewModel.deleteExercise(exercise.id) },
                    )
                }
            }
            OutlinedButton(
                onClick = viewModel::addExercise,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Добавить упражнение") }
            if (showErrors && day.exercises.isEmpty()) {
                Text("Добавь хотя бы одно упражнение", color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = {
                focus.clearFocus()
                viewModel.finishDay(onBack)
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(min = 52.dp),
        ) { Text("Сохранить день") }
    }
}

@Composable
private fun ExerciseEditor(
    exercise: ExerciseDraft,
    index: Int,
    showErrors: Boolean,
    onChange: (ExerciseDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val suffix = if (index == 0) "" else "-${index + 1}"
    val nameError = showErrors && exercise.name.isBlank()
    val setsError = showErrors && !exercise.sets.validRecommendation()
    val repsError = showErrors && !exercise.repetitions.validRecommendation()
    var expanded by rememberSaveable { mutableStateOf(exercise.sets.isNotBlank() || exercise.repetitions.isNotBlank()) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = exercise.name,
                    onValueChange = { onChange(exercise.copy(name = it)) },
                    label = { Text("Название упражнения") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("Введи название упражнения") }) else null,
                    modifier = Modifier.weight(1f).testTag("exercise-name$suffix"),
                )
                DeleteIconButton("Удалить упражнение ${index + 1}", onClick = onDelete)
            }
            TextButton(onClick = { expanded = !expanded }) { Text("Рекомендации") }
            if (expanded || setsError || repsError) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecommendationInput(
                        label = "Подходы",
                        value = exercise.sets,
                        onChange = { onChange(exercise.copy(sets = it)) },
                        isError = setsError,
                        modifier = Modifier.weight(1f).testTag("recommendation-sets$suffix"),
                    )
                    RecommendationInput(
                        label = "Повторения",
                        value = exercise.repetitions,
                        onChange = { onChange(exercise.copy(repetitions = it)) },
                        isError = repsError,
                        modifier = Modifier.weight(1f).testTag("recommendation-repetitions$suffix"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        supportingText = if (isError) ({ Text("Введи целое число от 1") }) else null,
        modifier = modifier,
    )
}
