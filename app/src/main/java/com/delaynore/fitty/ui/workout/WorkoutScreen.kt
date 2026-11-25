package com.delaynore.fitty.ui.workout

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.SET_WEIGHT_LABEL
import com.delaynore.fitty.ui.SetFields
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.SetTimer
import com.delaynore.fitty.ui.SwipeToRevealDelete
import com.delaynore.fitty.domain.formatRecommendation
import com.delaynore.fitty.domain.formatResult
import com.delaynore.fitty.ui.hasWeightField
import com.delaynore.fitty.ui.measureLabel
import com.delaynore.fitty.ui.measureTag
import com.delaynore.fitty.domain.toInput
import com.delaynore.fitty.ui.UiEventEffect
import kotlinx.coroutines.delay

@Composable
internal fun WorkoutScreen(id: Long, activeWorkout: ActiveWorkoutState, onBack: () -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(activeWorkout, id) { activeWorkout.open(id) }
    DisposableEffect(activeWorkout, id) {
        onDispose { activeWorkout.closeReplacement() }
    }
    val state by activeWorkout.uiState.collectAsStateWithLifecycle()
    val current = state.takeIf { it.sessionId == id } ?: WorkoutUiState()
    val workout = current.session
    val finished = !current.saving && workout?.summary?.finishedAtMillis != null
    val missing = current.sessionLoaded && !current.saving && workout == null
    LaunchedEffect(id, finished, missing) {
        when {
            finished -> onFinished()
            missing -> onBack()
        }
    }
    UiEventEffect(current.pendingEvent, activeWorkout::consumeEvent) { event ->
        if (event is WorkoutEvent.Finished && event.sessionId == id) onFinished()
    }
    val exercise = workout?.exercises?.getOrNull(current.exerciseIndex)
    WorkoutContent(
        workout = workout,
        exercise = exercise,
        exerciseIndex = current.exerciseIndex,
        rows = exercise?.let { current.rows(it.id) } ?: WorkoutRowDrafts(),
        timer = current.setTimer,
        savedPosition = current.savedPosition,
        saving = current.saving,
        error = current.error,
        pendingRemovals = current.pendingRemovals,
        editingSet = current.editingSet,
        restDeadlineMillis = current.restDeadlineMillis,
        remainingRestMillis = activeWorkout::remainingRestMillis,
        elapsedSetMillis = activeWorkout::elapsedSetMillis,
        onStartRest = activeWorkout::startRestTimer,
        onResetRest = activeWorkout::resetRestTimer,
        onExpireRest = activeWorkout::expireRestTimer,
        onClearFocusRequest = activeWorkout::clearFocusRequest,
        onStartSetTimer = { exerciseId, position -> activeWorkout.startSetTimer(id, exerciseId, position) },
        onStopSetTimer = activeWorkout::stopSetTimer,
        onDeleteRow = { current, position, number, logged -> activeWorkout.deleteRow(id, current, position, number, logged) },
        onChangeDraft = activeWorkout::changeDraft,
        onLogSet = { current, position -> activeWorkout.logSet(id, current, position) },
        onOpenSetEditor = activeWorkout::openSetEditor,
        onAddSetRow = activeWorkout::addSetRow,
        onRestoreRow = { activeWorkout.restoreRow(id, it) },
        onSelectExercise = activeWorkout::selectExercise,
        onFinish = { activeWorkout.finish(id) },
        onChangeSetEdit = activeWorkout::changeSetEdit,
        onCloseSetEditor = activeWorkout::closeSetEditor,
        onSaveSetEdit = { activeWorkout.saveSetEdit(id) },
        onBack = onBack,
        replacement = current.replacement,
        onOpenReplacement = activeWorkout::openReplacement,
        onCloseReplacement = activeWorkout::closeReplacement,
        onReplace = activeWorkout::replaceExercise,
    )
}

@Composable
private fun WorkoutContent(
    workout: WorkoutSession?,
    exercise: WorkoutExercise?,
    exerciseIndex: Int,
    rows: WorkoutRowDrafts,
    timer: SetTimer?,
    savedPosition: Int?,
    saving: Boolean,
    error: String?,
    pendingRemovals: List<RemovedWorkoutRow>,
    editingSet: WorkoutSetEdit?,
    restDeadlineMillis: Long?,
    remainingRestMillis: (Long) -> Long,
    elapsedSetMillis: (SetTimer) -> Long,
    onStartRest: () -> Unit,
    onResetRest: () -> Unit,
    onExpireRest: (Long) -> Unit,
    onClearFocusRequest: () -> Unit,
    onStartSetTimer: (Long, Int) -> Unit,
    onStopSetTimer: (String) -> Unit,
    onDeleteRow: (WorkoutExercise, Int, Int, Boolean) -> Unit,
    onChangeDraft: (WorkoutExercise, Int, SetInputField, String) -> Unit,
    onLogSet: (WorkoutExercise, Int) -> Unit,
    onOpenSetEditor: (WorkoutExercise, Int, LoggedSet) -> Unit,
    onAddSetRow: (Long, Int) -> Unit,
    onRestoreRow: (RemovedWorkoutRow) -> Unit,
    onSelectExercise: (Int) -> Unit,
    onFinish: () -> Unit,
    onChangeSetEdit: (SetInputField, String) -> Unit,
    onCloseSetEditor: () -> Unit,
    onSaveSetEdit: () -> Unit,
    onBack: () -> Unit,
    replacement: WorkoutReplacement?,
    onOpenReplacement: () -> Unit,
    onCloseReplacement: () -> Unit,
    onReplace: (CatalogExercise) -> Unit,
) {
    var showExercises by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        if (workout == null || exercise == null) {
            PageHeader("Тренировка", onBack)
            CircularProgressIndicator(Modifier.padding(20.dp))
        } else {
            val catalogId = exercise.catalogExerciseId
            val canReplace = catalogId != null && !exercise.hasEverLoggedSet
            val activeReplacement = replacement?.takeIf { it.exerciseId == exercise.id && it.catalogId == catalogId }
            WorkoutHeader(
                exercise = exercise,
                exerciseIndex = exerciseIndex,
                exerciseCount = workout.exercises.size,
                restDeadlineMillis = restDeadlineMillis,
                remainingRestMillis = remainingRestMillis,
                onStartRest = onStartRest,
                onResetRest = onResetRest,
                onExpireRest = onExpireRest,
                onBack = onBack,
                onReplace = if (canReplace) {
                    {
                        focus.clearFocus()
                        onOpenReplacement()
                    }
                } else null,
                saving = saving,
            )
            key(workout.summary.id, exercise.id, catalogId) {
                val measuredExercise = workout.exercises.firstOrNull { current -> timer?.matches(current) == true }
                WorkoutSetList(
                    exercise = exercise,
                    measuredExercise = measuredExercise,
                    rows = rows,
                    timer = timer,
                    savedPosition = savedPosition,
                    saving = saving,
                    elapsedSetMillis = elapsedSetMillis,
                    onStartTimer = { onStartSetTimer(exercise.id, it) },
                    onStopTimer = onStopSetTimer,
                    onClearFocusRequest = onClearFocusRequest,
                    onDeleteRow = { position, number, logged -> onDeleteRow(exercise, position, number, logged) },
                    onChangeDraft = { position, field, text -> onChangeDraft(exercise, position, field, text) },
                    onLogSet = { onLogSet(exercise, it) },
                    onOpenSetEditor = { number, set -> onOpenSetEditor(exercise, number, set) },
                    onAddSetRow = { onAddSetRow(exercise.id, it) },
                    modifier = Modifier.weight(1f),
                )
            }
            error?.takeUnless { activeReplacement != null && canReplace }?.let {
                Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
            }
            UndoRemovedRows(pendingRemovals, saving, onRestoreRow)
            ExerciseControls(
                index = exerciseIndex,
                lastIndex = workout.exercises.lastIndex,
                canFinish = workout.exercises.any { it.sets.isNotEmpty() },
                saving = saving,
                onSelect = {
                    focus.clearFocus()
                    onSelectExercise(it)
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
            if (activeReplacement != null && canReplace) {
                ExerciseReplacementSheet(
                    options = activeReplacement.options,
                    saving = saving,
                    error = error,
                    onSelect = onReplace,
                    onDismiss = onCloseReplacement,
                )
            }
            if (showExercises) {
                AllExercises(workout, onDismiss = { showExercises = false }) { index ->
                    onSelectExercise(index)
                    showExercises = false
                }
            }
            if (confirmFinish) {
                val setCount = workout.exercises.sumOf { it.sets.size }
                AlertDialog(
                    onDismissRequest = { if (!saving) confirmFinish = false },
                    title = { Text("Завершить тренировку?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${workout.summary.dayName}\nЗаписано подходов: $setCount")
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = onFinish,
                            enabled = !saving && setCount > 0,
                        ) { Text("Завершить тренировку") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmFinish = false }, enabled = !saving) { Text("Продолжить") }
                    },
                )
            }
            editingSet?.let { edit ->
                key(workout.summary.id, edit.version) {
                    EditSetSheet(
                        edit = edit,
                        saving = saving,
                        error = error,
                        onInput = onChangeSetEdit,
                        onDismiss = onCloseSetEditor,
                        onSave = onSaveSetEdit,
                    )
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
    remainingRestMillis: (Long) -> Long,
    onStartRest: () -> Unit,
    onResetRest: () -> Unit,
    onExpireRest: (Long) -> Unit,
    onBack: () -> Unit,
    onReplace: (() -> Unit)?,
    saving: Boolean,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnExpireRest by rememberUpdatedState(onExpireRest)
    var remainingMillis by remember(restDeadlineMillis, remainingRestMillis) {
        mutableLongStateOf(restDeadlineMillis?.let(remainingRestMillis) ?: 0L)
    }
    LaunchedEffect(restDeadlineMillis, lifecycle, remainingRestMillis) {
        if (restDeadlineMillis == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            remainingMillis = remainingRestMillis(restDeadlineMillis)
            while (remainingMillis > 0) {
                delay(minOf(remainingMillis, 100L))
                remainingMillis = remainingRestMillis(restDeadlineMillis)
            }
            currentOnExpireRest(restDeadlineMillis)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Назад" }) {
                    FittyGlyph(FittyIcon.Back)
                }
                Text(
                    "Упражнение ${exerciseIndex + 1} из $exerciseCount",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onReplace != null) {
                    TextButton(onClick = onReplace, enabled = !saving) { Text("Заменить упражнение") }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    formatRecommendation(exercise.recommendedSets, exercise.goal)?.let {
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
    exercise: WorkoutExercise,
    measuredExercise: WorkoutExercise?,
    rows: WorkoutRowDrafts,
    timer: SetTimer?,
    savedPosition: Int?,
    saving: Boolean,
    elapsedSetMillis: (SetTimer) -> Long,
    onStartTimer: (Int) -> Unit,
    onStopTimer: (String) -> Unit,
    onClearFocusRequest: () -> Unit,
    onDeleteRow: (Int, Int, Boolean) -> Unit,
    onChangeDraft: (Int, SetInputField, String) -> Unit,
    onLogSet: (Int) -> Unit,
    onOpenSetEditor: (Int, LoggedSet) -> Unit,
    onAddSetRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val logged = remember(exercise.sets) { exercise.sets.associateBy(LoggedSet::position) }
    val removedPositions = remember(exercise.removedPositions) { exercise.removedPositions.sorted() }
    val lastPosition = maxOf(
        exercise.recommendedSets ?: 4,
        rows.addedThrough,
        exercise.sets.lastOrNull()?.position ?: 0,
        removedPositions.lastOrNull() ?: 0,
    )
    val rowCount = lastPosition - removedPositions.size
    val activePosition = (1..lastPosition).firstOrNull { it !in logged && it !in exercise.removedPositions }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val advanceFocus = savedPosition != null && savedPosition in logged
    LaunchedEffect(advanceFocus, activePosition) {
        if (advanceFocus) {
            withFrameNanos { }
            if (activePosition == null) {
                focus.clearFocus()
                onClearFocusRequest()
            } else {
                listState.requestScrollToItem(activePosition - 1 - removedPositions.count { it < activePosition })
            }
        }
    }
    if (exercise.type == ExerciseType.DURATION || measuredExercise != null) {
        WorkoutSetTimerControls(
            timer = timer,
            otherExerciseName = measuredExercise?.takeIf { it.id != exercise.id }?.name,
            enabled = !saving && (timer != null || activePosition != null),
            elapsedMillis = elapsedSetMillis,
            onStart = {
                if (activePosition != null) {
                    focus.clearFocus()
                    onStartTimer(activePosition)
                }
            },
            onStop = onStopTimer,
        )
    }
    SetHeadings(exercise.type, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
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
                    onClearFocusRequest()
                    onDeleteRow(position, number, loggedSet != null)
                },
            ) {
                if (loggedSet == null) {
                    val active = position == activePosition
                    val measuring = timer?.let { it.matches(exercise) && it.position == position } == true
                    key(rows.resetVersions[position]) {
                        DraftSetRow(
                            number = number,
                            input = rows.inputs[position] ?: SetInput(exercise.type),
                            enabled = active && !measuring && !saving,
                            onInput = { field, text -> onChangeDraft(position, field, text) },
                            onLog = { onLogSet(position) },
                            firstFieldModifier = if (active) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                    if (active && advanceFocus && !measuring && !saving) {
                        LaunchedEffect(position) {
                            withFrameNanos { }
                            focusRequester.requestFocus()
                            onClearFocusRequest()
                        }
                    }
                } else {
                    LoggedSetRow(number, loggedSet) {
                        focus.clearFocus()
                        onOpenSetEditor(number, loggedSet)
                    }
                }
            }
        }
        if (activePosition == null && lastPosition < Int.MAX_VALUE) {
            item {
                OutlinedButton(onClick = { onAddSetRow(lastPosition) }, enabled = !saving) {
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
                            removed.set != null -> removed.set.result.formatResult()
                            removed.draft != null -> "Черновик: ${removed.draft.formatDraft()}"
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
private fun SetHeadings(type: ExerciseType, modifier: Modifier = Modifier) {
    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("№", Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (type.hasWeightField) {
                Text(SET_WEIGHT_LABEL, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(type.measureLabel, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("✓", Modifier.width(48.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DraftSetRow(
    number: Int,
    input: SetInput,
    enabled: Boolean,
    onInput: (SetInputField, String) -> Unit,
    onLog: () -> Unit,
    firstFieldModifier: Modifier,
) {
    val hasWeight = input.type.hasWeightField
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(number.toString(), Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SetFields(
            input = input,
            onFieldChange = onInput,
            enabled = enabled,
            weightModifier = Modifier.weight(1f).then(firstFieldModifier).testTag("workout-weight-$number")
                .semantics { contentDescription = "Вес, подход $number" },
            measureModifier = Modifier.weight(1f)
                .then(if (hasWeight) Modifier else firstFieldModifier)
                .testTag("workout-${input.type.measureTag}-$number")
                .semantics { contentDescription = "${input.type.measureLabel}, подход $number" },
            showLabels = false,
        )
        FilledTonalIconButton(
            onClick = onLog,
            enabled = enabled,
            modifier = Modifier.width(48.dp).semantics { contentDescription = "Записать подход $number" },
        ) { FittyGlyph(FittyIcon.Check) }
    }
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
            val input = set.result.toInput()
            if (set.result is SetResult.WeightAndReps) {
                Text(input.weight, Modifier.weight(1f).testTag("workout-logged-weight-$number"))
            }
            Text(input.measure, Modifier.weight(1f).testTag("workout-logged-${input.type.measureTag}-$number"))
            Row(Modifier.width(48.dp), horizontalArrangement = Arrangement.Center) { FittyGlyph(FittyIcon.Check) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(
    edit: WorkoutSetEdit,
    saving: Boolean,
    error: String?,
    onInput: (SetInputField, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(edit.exerciseName, style = MaterialTheme.typography.titleLarge)
            Text("Подход ${edit.number}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetFields(
                    edit.input,
                    onFieldChange = onInput,
                    enabled = !saving,
                    weightModifier = Modifier.weight(1f).testTag("workout-edit-weight"),
                    measureModifier = Modifier.weight(1f).testTag("workout-edit-${edit.input.type.measureTag}"),
                    showLabels = true,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}
