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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.SwipeToRevealDelete

@Composable
internal fun ProgramEditorScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onEditDay: () -> Unit,
    onSettings: () -> Unit,
    onSaved: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxHeight()) {
        ScreenColumn(Modifier.weight(1f)) {
            PageHeader(
                if (viewModel.editingExistingProgram) "Редактирование программы" else "Новая программа",
                onBack,
            ) {
                if (viewModel.hasChanges && !viewModel.saving) {
                    EditorSettingsButton {
                        focus.clearFocus()
                        onSettings()
                    }
                }
            }
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
private fun EditorSettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Настройки" }) {
        FittyGlyph(FittyIcon.Settings)
    }
}

@Composable
internal fun TrainingDayEditorScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onPickExercise: (Long?) -> Unit,
) {
    val day = viewModel.editingDay ?: return
    TrainingDayEditorContent(
        day = day,
        showErrors = viewModel.showDayErrors,
        onBack = onBack,
        onSettings = if (viewModel.hasChanges) onSettings else null,
        onPickExercise = onPickExercise,
        onDayNameChange = viewModel::changeDayName,
        onChangeExercise = viewModel::changeExercise,
        onDeleteExercise = viewModel::deleteExercise,
        onMoveExercise = viewModel::moveExercise,
        onSave = { viewModel.finishDay(onBack) },
    )
}

@Composable
internal fun TrainingDayEditorContent(
    day: TrainingDayDraft,
    showErrors: Boolean,
    onBack: () -> Unit,
    onSettings: (() -> Unit)?,
    onPickExercise: (Long?) -> Unit,
    onDayNameChange: (String) -> Unit,
    onChangeExercise: (ExerciseDraft) -> Unit,
    onDeleteExercise: (Long) -> Unit,
    onMoveExercise: (Long, Int) -> Unit,
    onSave: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(Modifier.fillMaxHeight()) {
        ScreenColumn(
            modifier = Modifier.weight(1f).onGloballyPositioned { viewport = it }.testTag("day-editor-scroll"),
            scrollState = scrollState,
        ) {
            PageHeader("Тренировочный день", onBack) {
                if (onSettings != null) {
                    EditorSettingsButton {
                        focus.clearFocus()
                        onSettings()
                    }
                }
            }
            OutlinedTextField(
                value = day.name,
                onValueChange = onDayNameChange,
                label = { Text("Название дня") },
                singleLine = true,
                isError = showErrors && day.name.isBlank(),
                supportingText = if (showErrors && day.name.isBlank()) ({ Text("Введи название дня") }) else null,
                modifier = Modifier.fillMaxWidth().testTag("day-name"),
            )
            DayExercisesEditor(
                day = day,
                showErrors = showErrors,
                scrollState = scrollState,
                viewport = { viewport },
                onPickExercise = onPickExercise,
                onChange = onChangeExercise,
                onDelete = onDeleteExercise,
                onMove = onMoveExercise,
            )
            if (showErrors && day.exercises.isEmpty()) {
                Text("Добавь хотя бы одно упражнение", color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = {
                focus.clearFocus()
                onSave()
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
                .heightIn(min = 52.dp),
        ) { Text("Сохранить день") }
    }
}
