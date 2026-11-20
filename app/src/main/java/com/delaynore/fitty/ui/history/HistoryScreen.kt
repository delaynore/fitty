package com.delaynore.fitty.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.SetInputErrors
import com.delaynore.fitty.ui.formatWeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryDateFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("ru"))
private val HistoryTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru"))

@Composable
internal fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (Long) -> Unit) {
    val state by viewModel.history.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader("История") }
        val history = state
        when {
            history == null -> item { CircularProgressIndicator() }
            history.isEmpty() -> item {
                Text("Завершённых тренировок пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(history, key = { it.id }) { workout ->
                Card(
                    onClick = { onOpen(workout.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        workout.finishedAtMillis?.let {
                            WorkoutDate(it)
                        }
                        Text(workout.dayName, style = MaterialTheme.typography.titleLarge)
                        Text(workout.programName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkoutDetailsScreen(id: Long, viewModel: HistoryViewModel, onBack: () -> Unit) {
    val details by remember(viewModel, id) { viewModel.workout(id) }.collectAsStateWithLifecycle(null)
    var editingSetId by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    var confirmingDeletion by rememberSaveable(id) { mutableStateOf(false) }
    val workout = details?.workout?.takeIf { it.summary.id == id }
    val closeEditor = {
        editingSetId = null
        viewModel.clearError()
    }
    val confirmDeletion = {
        closeEditor()
        confirmingDeletion = true
    }

    LaunchedEffect(details) {
        if (details != null && workout == null) onBack()
    }

    ScreenColumn {
        PageHeader(workout?.summary?.dayName ?: "Тренировка", onBack)
        if (workout == null) {
            CircularProgressIndicator()
        } else {
            workout.summary.finishedAtMillis?.let { WorkoutDate(it) }
            Text(workout.summary.programName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            workout.exercises.forEach { exercise ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(
                            exercise.name,
                            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (exercise.sets.isEmpty()) {
                            Text(
                                "Без записанных подходов",
                                Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        exercise.sets.forEachIndexed { index, set ->
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !viewModel.saving) {
                                        viewModel.clearError()
                                        editingSetId = set.id
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Подход ${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatWeight(set.weightKg)} кг × ${set.repetitions}")
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = confirmDeletion,
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить тренировку", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    val exercise = workout?.exercises?.find { item -> item.sets.any { it.id == editingSetId } }
    val set = exercise?.sets?.find { it.id == editingSetId }
    if (workout != null && exercise != null && set != null) {
        key(set.id) {
            EditSetSheet(
                exerciseName = exercise.name,
                set = set,
                lastSet = workout.exercises.sumOf { it.sets.size } == 1,
                saving = viewModel.saving,
                error = viewModel.error,
                onDismiss = closeEditor,
                setErrors = viewModel.setErrors,
                onEdit = { weight, repetitions -> viewModel.changeSetInput(set, weight, repetitions) },
                onSave = { weight, repetitions -> viewModel.saveSet(id, set, weight, repetitions, closeEditor) },
                onDeleteSet = { viewModel.deleteSet(id, set.id, closeEditor) },
                onDeleteWorkout = confirmDeletion,
            )
        }
    }

    if (confirmingDeletion && workout != null) {
        val closeConfirmation = {
            if (!viewModel.saving) {
                viewModel.clearError()
                confirmingDeletion = false
            }
        }
        AlertDialog(
            onDismissRequest = closeConfirmation,
            title = { Text("Удалить тренировку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(workout.summary.dayName, style = MaterialTheme.typography.titleMedium)
                    workout.summary.finishedAtMillis?.let { WorkoutDate(it) }
                    Text("Все записанные подходы будут удалены из истории.")
                    viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteWorkout(id) }, enabled = !viewModel.saving) {
                    Text("Удалить тренировку", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = closeConfirmation, enabled = !viewModel.saving) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun WorkoutDate(millis: Long) {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            dateTime.format(HistoryDateFormat),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            dateTime.format(HistoryTimeFormat),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(
    exerciseName: String,
    set: LoggedSet,
    lastSet: Boolean,
    saving: Boolean,
    error: String?,
    setErrors: SetInputErrors?,
    onDismiss: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSave: (String, String) -> Unit,
    onDeleteSet: () -> Unit,
    onDeleteWorkout: () -> Unit,
) {
    var weight by rememberSaveable { mutableStateOf(formatWeight(set.weightKg)) }
    var repetitions by rememberSaveable { mutableStateOf(set.repetitions.toString()) }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Изменить подход", style = MaterialTheme.typography.headlineSmall)
            Text(exerciseName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = weight,
                onValueChange = {
                    weight = it
                    onEdit(weight, repetitions)
                },
                modifier = Modifier.fillMaxWidth().testTag("history-set-weight"),
                label = { Text("Вес, кг") },
                isError = setErrors?.weight != null,
                supportingText = setErrors?.weight?.let { message -> { Text(message) } },
                enabled = !saving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = repetitions,
                onValueChange = {
                    repetitions = it
                    onEdit(weight, repetitions)
                },
                modifier = Modifier.fillMaxWidth().testTag("history-set-repetitions"),
                label = { Text("Повторения") },
                isError = setErrors?.repetitions != null,
                supportingText = setErrors?.repetitions?.let { message -> { Text(message) } },
                enabled = !saving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onSave(weight, repetitions) }, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                Text("Сохранить")
            }
            if (lastSet) {
                Text(
                    "Это последний подход. Чтобы убрать его из истории, удали тренировку целиком.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDeleteWorkout, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                    Text("Удалить тренировку целиком", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDeleteSet, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                    Text("Удалить подход", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
