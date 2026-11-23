package com.delaynore.fitty.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.Equipment
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.MuscleGroup
import com.delaynore.fitty.data.exercises.TechniqueDifficulty
import com.delaynore.fitty.ui.DraftTextField
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun ExerciseEditorScreen(viewModel: ExerciseEditorViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft ?: return
    key(uiState.draftVersion) {
        ExerciseEditorContent(
            state = draft,
            onNameChange = viewModel::changeName,
            onTypeSelected = viewModel::selectType,
            onDetailsChange = viewModel::changeDetails,
            onBack = onBack,
            onSave = viewModel::save,
        )
    }
}

@Composable
internal fun ExerciseEditorContent(
    state: ExerciseEditorState,
    onNameChange: (String) -> Unit,
    onTypeSelected: (ExerciseType) -> Unit,
    onDetailsChange: ((CatalogExercise) -> CatalogExercise) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    ScreenColumn {
        PageHeader(state.details?.name ?: "Создать упражнение", onBack)
        if (state.details != null) {
            ExerciseDetailsFields(state.details, !state.saving, onDetailsChange)
        } else {
            DraftTextField(
                value = state.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving,
                label = { Text("Название упражнения") },
                singleLine = true,
            )
            Text("Тип записи", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.entries.forEach { type ->
                    FilterChip(
                        selected = type == state.type,
                        onClick = { onTypeSelected(type) },
                        enabled = !state.saving,
                        label = { Text(type.label) },
                    )
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSave, enabled = state.canSave) {
            Text(if (state.details == null) "Сохранить упражнение" else "Сохранить сведения")
        }
    }
}

@Composable
private fun ExerciseDetailsFields(
    exercise: CatalogExercise,
    enabled: Boolean,
    onChange: ((CatalogExercise) -> CatalogExercise) -> Unit,
) {
    Text("Сложность техники", style = MaterialTheme.typography.titleSmall)
    ExerciseDetailMenu(
        selected = exercise.difficulty,
        options = TechniqueDifficulty.entries,
        optionLabel = { it.label },
        emptyLabel = "Не указана",
        enabled = enabled,
    ) { difficulty -> onChange { it.copy(difficulty = difficulty) } }
    DraftTextField(
        value = exercise.technique.orEmpty(),
        onValueChange = { text -> onChange { it.copy(technique = text) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Техника выполнения") },
    )
    Text("Оборудование", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Equipment.entries.forEach { equipment ->
            FilterChip(
                selected = equipment in exercise.equipment,
                onClick = { onChange { current ->
                    val selected = when {
                        equipment in current.equipment -> current.equipment - equipment
                        equipment == Equipment.NONE -> setOf(equipment)
                        else -> current.equipment - Equipment.NONE + equipment
                    }
                    current.copy(equipment = selected)
                } },
                enabled = enabled,
                label = { Text(equipment.label) },
            )
        }
    }
    Text("Основная группа мышц", style = MaterialTheme.typography.titleSmall)
    ExerciseDetailMenu(
        selected = exercise.primaryMuscle,
        options = MuscleGroup.entries,
        optionLabel = { it.label },
        emptyLabel = "Выбрать группу",
        enabled = enabled,
    ) { muscle ->
        onChange { current ->
            val assisting = if (muscle == null) current.assistingMuscles else current.assistingMuscles - muscle
            current.copy(primaryMuscle = muscle, assistingMuscles = assisting)
        }
    }
    Text("Вспомогательные группы мышц", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MuscleGroup.entries.filter { it != exercise.primaryMuscle }.forEach { muscle ->
            FilterChip(
                selected = muscle in exercise.assistingMuscles,
                onClick = {
                    onChange { current ->
                        val muscles = if (muscle in current.assistingMuscles) {
                            current.assistingMuscles - muscle
                        } else {
                            current.assistingMuscles + muscle
                        }
                        current.copy(assistingMuscles = muscles)
                    }
                },
                enabled = enabled,
                label = { Text(muscle.label) },
            )
        }
    }
}

@Composable
private fun <T> ExerciseDetailMenu(
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    emptyLabel: String,
    enabled: Boolean,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(selected?.let(optionLabel) ?: emptyLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(emptyLabel) }, onClick = {
                onSelect(null)
                expanded = false
            })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}
