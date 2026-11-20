package com.delaynore.fitty.ui.workout

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.SwipeToRevealDelete
import com.delaynore.fitty.ui.formatRecommendation
import com.delaynore.fitty.ui.formatWeight
import kotlinx.coroutines.delay

@Composable
internal fun WorkoutScreen(id: Long, viewModel: WorkoutViewModel, onBack: () -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(id) { viewModel.open(id) }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val workout = session?.takeIf { it.summary.id == id }
    val exercise = workout?.exercises?.getOrNull(viewModel.exerciseIndex)
    var showExercises by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, LoggedSet>?>(null) }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        if (workout == null || exercise == null) {
            PageHeader("Тренировка", onBack)
            CircularProgressIndicator(Modifier.padding(20.dp))
        } else {
            WorkoutHeader(
                exercise = exercise,
                exerciseIndex = viewModel.exerciseIndex,
                exerciseCount = workout.exercises.size,
                restDeadlineMillis = viewModel.restDeadlineMillis,
                onStartRest = viewModel::startRestTimer,
                onResetRest = viewModel::resetRestTimer,
                onBack = onBack,
            )
            SetHeadings(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            key(exercise.id) {
                WorkoutSetList(id, exercise, viewModel, { number, set -> editing = number to set }, Modifier.weight(1f))
            }
            viewModel.error?.let {
                Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
            }
            UndoRemovedRows(viewModel.pendingRemovals, viewModel.saving) { viewModel.restoreRow(id, it) }
            ExerciseControls(
                index = viewModel.exerciseIndex,
                lastIndex = workout.exercises.lastIndex,
                canFinish = workout.exercises.any { it.sets.isNotEmpty() },
                saving = viewModel.saving,
                onSelect = {
                    focus.clearFocus()
                    viewModel.selectExercise(it)
                },
                onShowExercises = {
                    focus.clearFocus()
                    showExercises = true
                },
                onFinish = {
                    focus.clearFocus()
                    confirmFinish = true
                },
            )
            if (showExercises) {
                AllExercises(workout, onDismiss = { showExercises = false }) { index ->
                    viewModel.selectExercise(index)
                    showExercises = false
                }
            }
            if (confirmFinish) {
                val setCount = workout.exercises.sumOf { it.sets.size }
                AlertDialog(
                    onDismissRequest = { if (!viewModel.saving) confirmFinish = false },
                    title = { Text("Завершить тренировку?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${workout.summary.dayName}\nЗаписано подходов: $setCount")
                            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.finish(id) {
                                    confirmFinish = false
                                    onFinished()
                                }
                            },
                            enabled = !viewModel.saving && setCount > 0,
                        ) { Text("Завершить тренировку") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmFinish = false }, enabled = !viewModel.saving) { Text("Продолжить") }
                    },
                )
            }
            editing?.let { (number, set) ->
                EditSetSheet(exercise, number, set, viewModel.saving, viewModel.error, onDismiss = { editing = null }) { updated ->
                    viewModel.editSet(id, exercise, updated) { editing = null }
                }
            }
        }
    }
}

@Composable
private fun WorkoutHeader(
    exercise: WorkoutExercise,
    exerciseIndex: Int,
    exerciseCount: Int,
    restDeadlineMillis: Long?,
    onStartRest: () -> Unit,
    onResetRest: () -> Unit,
    onBack: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var remainingMillis by remember(restDeadlineMillis) {
        mutableLongStateOf(remainingRestMillis(restDeadlineMillis))
    }
    LaunchedEffect(restDeadlineMillis, lifecycle) {
        if (restDeadlineMillis == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            remainingMillis = remainingRestMillis(restDeadlineMillis)
            while (remainingMillis > 0) {
                delay(minOf(remainingMillis, 100L))
                remainingMillis = remainingRestMillis(restDeadlineMillis)
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Назад" }) {
                    FittyGlyph(FittyIcon.Back)
                }
                Text("Упражнение ${exerciseIndex + 1} из $exerciseCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    formatRecommendation(exercise.recommendedSets, exercise.recommendedRepetitions)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                    }
                }
                RestTimerControls(remainingMillis, onStartRest, onResetRest)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (remainingMillis > 0) {
                val fraction = remainingMillis.toFloat() / REST_DURATION_MILLIS
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outlineVariant).progressSemantics(fraction)) {
                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

private fun remainingRestMillis(deadlineMillis: Long?): Long =
    deadlineMillis?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) } ?: 0L

@Composable
private fun RestTimerControls(remainingMillis: Long, onStart: () -> Unit, onReset: () -> Unit) {
    val running = remainingMillis > 0
    val seconds = (remainingMillis + 999) / 1_000
    val digits = if (running) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "2:00"
    Text(
        text = digits,
        modifier = if (running) Modifier else Modifier.clearAndSetSemantics {},
        style = MaterialTheme.typography.titleLarge,
        fontFamily = FontFamily.Monospace,
        color = if (running) MaterialTheme.colorScheme.primary else Color.Transparent,
        maxLines = 1,
        softWrap = false,
    )
    FilledTonalIconButton(
        onClick = if (running) onReset else onStart,
        modifier = Modifier.semantics {
            contentDescription = if (running) "Сбросить таймер отдыха" else "Запустить таймер отдыха"
        },
    ) {
        FittyGlyph(if (running) FittyIcon.Stop else FittyIcon.Stopwatch)
    }
}

@Composable
private fun WorkoutSetList(
    sessionId: Long,
    exercise: WorkoutExercise,
    viewModel: WorkoutViewModel,
    onEdit: (Int, LoggedSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(exercise.id) { viewModel.rows(exercise.id) }
    val logged = remember(exercise.sets) { exercise.sets.associateBy(LoggedSet::position) }
    val removedPositions = rows.removedPositions.keys.sorted()
    val lastPosition = maxOf(
        exercise.recommendedSets ?: 4,
        rows.addedThrough,
        exercise.sets.lastOrNull()?.position ?: 0,
        removedPositions.lastOrNull() ?: 0,
    )
    val rowCount = lastPosition - removedPositions.size
    val activePosition = (1..lastPosition).firstOrNull { it !in logged && it !in rows.removedPositions }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val savedPosition = viewModel.savedPosition
    val advanceFocus = savedPosition != null && savedPosition in logged
    LaunchedEffect(advanceFocus, activePosition) {
        if (advanceFocus) {
            withFrameNanos { }
            if (activePosition == null) {
                focus.clearFocus()
                viewModel.clearFocusRequest()
            } else {
                listState.requestScrollToItem(activePosition - 1 - removedPositions.count { it < activePosition })
            }
        }
    }
    LazyColumn(
        modifier.fillMaxWidth().testTag("workout-sets"),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rowCount, key = { rowPosition(it, removedPositions) }) { index ->
            val position = rowPosition(index, removedPositions)
            val number = index + 1
            val loggedSet = logged[position]
            SwipeToRevealDelete(
                description = "Удалить подход $number",
                modifier = Modifier.testTag("workout-set-row-$number"),
                onDelete = {
                    focus.clearFocus()
                    viewModel.clearFocusRequest()
                    viewModel.deleteRow(sessionId, exercise, position, number, loggedSet != null)
                },
            ) {
                if (loggedSet == null) {
                    val active = position == activePosition
                    DraftSetRow(
                        number = number,
                        input = rows.drafts[position] ?: SetInput(),
                        enabled = active && !viewModel.saving,
                        onInput = { rows.drafts[position] = it },
                        onLog = { viewModel.logSet(sessionId, exercise.id, position) },
                        weightModifier = if (active) Modifier.focusRequester(focusRequester) else Modifier,
                    )
                    if (active && advanceFocus && !viewModel.saving) {
                        LaunchedEffect(position) {
                            withFrameNanos { }
                            focusRequester.requestFocus()
                            viewModel.clearFocusRequest()
                        }
                    }
                } else {
                    LoggedSetRow(number, loggedSet) {
                        focus.clearFocus()
                        onEdit(number, loggedSet)
                    }
                }
            }
        }
        if (activePosition == null && lastPosition < Int.MAX_VALUE) {
            item {
                OutlinedButton(onClick = { rows.addedThrough = lastPosition + 1 }, enabled = !viewModel.saving) {
                    Text("Ещё подход")
                }
            }
        }
    }
}

private fun rowPosition(index: Int, removedPositions: List<Int>): Int {
    var position = index + 1
    for (removed in removedPositions) {
        if (removed > position) break
        position++
    }
    return position
}

@Composable
private fun UndoRemovedRows(rows: List<RemovedWorkoutRow>, saving: Boolean, onRestore: (RemovedWorkoutRow) -> Unit) {
    if (rows.isEmpty()) return
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 224.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rows, key = { "${it.exerciseId}-${it.position}" }) { removed ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(
                            "Удалён подход ${removed.number} · ${removed.exerciseName}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val values = when {
                            removed.set != null -> "${formatWeight(removed.set.weightKg)} кг × ${removed.set.repetitions}"
                            removed.draft != null -> "Черновик: ${removed.draft.weight.ifBlank { "−" }} кг × ${removed.draft.repetitions.ifBlank { "−" }}"
                            else -> "Пустая строка"
                        }
                        Text(values, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = { onRestore(removed) },
                        enabled = !saving,
                        modifier = Modifier.semantics {
                            contentDescription = "Вернуть подход ${removed.number}, ${removed.exerciseName}"
                        },
                    ) { Text("Вернуть") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseControls(
    index: Int,
    lastIndex: Int,
    canFinish: Boolean,
    saving: Boolean,
    onSelect: (Int) -> Unit,
    onShowExercises: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            enabled = index > 0 && !saving,
            onClick = { onSelect(index - 1) },
            modifier = Modifier.semantics { contentDescription = "Предыдущее упражнение" },
        ) { FittyGlyph(FittyIcon.Back) }
        OutlinedButton(onClick = onShowExercises, modifier = Modifier.weight(1f), enabled = !saving) {
            Text("Все упражнения")
        }
        if (index == lastIndex) {
            Button(onClick = onFinish, enabled = canFinish && !saving) { Text("Завершить") }
        } else {
            FilledTonalIconButton(
                enabled = !saving,
                onClick = { onSelect(index + 1) },
                modifier = Modifier.semantics { contentDescription = "Следующее упражнение" },
            ) {
                Row(Modifier.rotate(180f)) { FittyGlyph(FittyIcon.Back) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllExercises(workout: WorkoutSession, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Все упражнения", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(20.dp)) {
            itemsIndexed(workout.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                Surface(
                    onClick = { onSelect(index) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${index + 1}. ${exercise.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Записано подходов: ${exercise.sets.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetHeadings(modifier: Modifier = Modifier) {
    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("№", Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Вес, кг", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Повторения", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("✓", Modifier.width(48.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DraftSetRow(
    number: Int,
    input: SetInput,
    enabled: Boolean,
    onInput: (SetInput) -> Unit,
    onLog: () -> Unit,
    weightModifier: Modifier,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(number.toString(), Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetFields(
            input = input,
            onInput = onInput,
            enabled = enabled,
            weightModifier = Modifier.weight(1f).then(weightModifier).testTag("workout-weight-$number")
                .semantics { contentDescription = "Вес, подход $number" },
            repetitionsModifier = Modifier.weight(1f).testTag("workout-repetitions-$number")
                .semantics { contentDescription = "Повторения, подход $number" },
        )
        FilledTonalIconButton(
            onClick = onLog,
            enabled = enabled,
            modifier = Modifier.width(48.dp).semantics { contentDescription = "Записать подход $number" },
        ) { FittyGlyph(FittyIcon.Check) }
    }
}

@Composable
private fun SetFields(
    input: SetInput,
    onInput: (SetInput) -> Unit,
    enabled: Boolean,
    weightModifier: Modifier,
    repetitionsModifier: Modifier,
    showLabels: Boolean = false,
) {
    val invalidWeight = input.validated && input.parsedWeight == null
    val invalidRepetitions = input.validated && input.parsedRepetitions == null
    OutlinedTextField(
        value = input.weight,
        onValueChange = { onInput(input.copy(weight = it)) },
        enabled = enabled,
        label = if (showLabels) ({ Text("Вес, кг") }) else null,
        singleLine = true,
        isError = invalidWeight,
        supportingText = if (invalidWeight) ({ Text("Введи вес, например 62,5") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = weightModifier,
    )
    OutlinedTextField(
        value = input.repetitions,
        onValueChange = { onInput(input.copy(repetitions = it)) },
        enabled = enabled,
        label = if (showLabels) ({ Text("Повторения") }) else null,
        singleLine = true,
        isError = invalidRepetitions,
        supportingText = if (invalidRepetitions) ({ Text("Целое число от 1") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = repetitionsModifier,
    )
}

@Composable
private fun LoggedSetRow(number: Int, set: LoggedSet, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.semantics { contentDescription = "Изменить подход $number" },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(number.toString(), Modifier.width(30.dp).padding(start = 8.dp))
            Text(formatWeight(set.weightKg), Modifier.weight(1f).testTag("workout-logged-weight-$number"))
            Text(set.repetitions.toString(), Modifier.weight(1f).testTag("workout-logged-repetitions-$number"))
            Row(Modifier.width(48.dp), horizontalArrangement = Arrangement.Center) { FittyGlyph(FittyIcon.Check) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(
    exercise: WorkoutExercise,
    number: Int,
    set: LoggedSet,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (LoggedSet) -> Unit,
) {
    var input by remember(set) { mutableStateOf(set.toInput()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge)
            Text("Подход $number", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetFields(
                    input,
                    onInput = { input = it },
                    enabled = !saving,
                    weightModifier = Modifier.weight(1f).testTag("workout-edit-weight"),
                    repetitionsModifier = Modifier.weight(1f).testTag("workout-edit-repetitions"),
                    showLabels = true,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val updated = input.toLoggedSet(set.position)
                    if (updated == null) input = input.copy(validated = true) else onSave(updated)
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}
