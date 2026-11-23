package com.delaynore.fitty.ui.programs

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.ui.DraftTextField
import com.delaynore.fitty.ui.DeleteIconButton
import com.delaynore.fitty.ui.formatRecommendation
import com.delaynore.fitty.ui.measureLabel
import com.delaynore.fitty.ui.measureTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RowSpacing = 6.dp
private val RowShape = RoundedCornerShape(12.dp)
private const val ReorderDuration = 180
private const val AutoScrollIntervalMillis = 16L
private val AutoScrollEdge = 48.dp
private val AutoScrollStep = 12.dp

@Composable
internal fun DayExercisesEditor(
    day: TrainingDayDraft,
    showErrors: Boolean,
    scrollState: ScrollState,
    viewport: () -> LayoutCoordinates?,
    onPickExercise: (Long?) -> Unit,
    onChange: (Long, (ExerciseDraft) -> ExerciseDraft) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
) {
    val focus = LocalFocusManager.current
    var expandedId by rememberSaveable(day.id) { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReorderableExerciseRows(day.exercises, scrollState, viewport, onMove) { exercise, index, lifted, handle, modifier ->
            val expanded = expandedId == exercise.id
            ExerciseEditor(
                exercise = exercise,
                index = index,
                expanded = expanded,
                lifted = lifted,
                showErrors = showErrors,
                onToggle = {
                    focus.clearFocus()
                    expandedId = if (expandedId == exercise.id) null else exercise.id
                },
                onChange = { change -> onChange(exercise.id, change) },
                onReplace = {
                    focus.clearFocus()
                    onPickExercise(exercise.id)
                },
                onDelete = {
                    if (expandedId == exercise.id) expandedId = null
                    onDelete(exercise.id)
                },
                handle = handle,
                modifier = modifier,
            )
        }
        OutlinedButton(
            onClick = {
                focus.clearFocus()
                onPickExercise(null)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Добавить упражнение") }
    }
}

@Composable
private fun ReorderableExerciseRows(
    exercises: List<ExerciseDraft>,
    scrollState: ScrollState,
    viewport: () -> LayoutCoordinates?,
    onMove: (Long, Int) -> Unit,
    row: @Composable (ExerciseDraft, Int, Boolean, Modifier, Modifier) -> Unit,
) {
    class ExerciseDrag(val id: Long, val height: Int, start: Float, index: Int) {
        var top by mutableFloatStateOf(start)
        var pointerY by mutableFloatStateOf(Float.NaN)
        var destination by mutableIntStateOf(index)
        var moving by mutableStateOf(true)
    }

    val heights = remember { mutableStateMapOf<Long, Int>() }
    var drag by remember { mutableStateOf<ExerciseDrag?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = with(density) { RowSpacing.roundToPx() }
    val edge = with(density) { AutoScrollEdge.toPx() }
    val step = with(density) { AutoScrollStep.toPx() }
    val ids = exercises.map { it.id }
    val baseTops = rowTops(ids, heights, spacing)
    val moving = drag?.takeIf { it.moving }
    val previewIds = if (moving == null) ids else ids.withMove(moving.id, moving.destination)
    val previewTops = rowTops(previewIds, heights, spacing)
    val startDrag by rememberUpdatedState<(Long) -> Unit> { id ->
        if (drag == null && ids.size > 1 && ids.all { it in heights }) {
            drag = ExerciseDrag(id, heights.getValue(id), baseTops.getValue(id), ids.indexOf(id))
        }
    }
    val moveDrag by rememberUpdatedState<(Long, Float) -> Unit> { id, delta ->
        drag?.takeIf { it.moving && it.id == id }?.let { current ->
            val listHeight = ids.sumOf { heights.getValue(it) } + spacing * (ids.size - 1)
            current.top = (current.top + delta).coerceIn(0f, (listHeight - current.height).toFloat())
            val source = ids.indexOf(id)
            var destination = source
            ids.forEachIndexed { index, neighbor ->
                val midpoint = baseTops.getValue(neighbor) + heights.getValue(neighbor) / 2f
                if (index < source && current.top < midpoint) destination--
                if (index > source && current.top + current.height > midpoint) destination++
            }
            current.destination = destination
        }
    }
    val finishDrag by rememberUpdatedState<(Long, Boolean) -> Unit> { id, commit ->
        drag?.takeIf { it.moving && it.id == id }?.let { current ->
            // The final gesture event can arrive before Compose has redrawn the slot.
            val top = if (commit) {
                rowTops(ids.withMove(id, current.destination), heights, spacing).getValue(id)
            } else {
                baseTops.getValue(id)
            }
            current.moving = false
            if (commit) onMove(current.id, current.destination)
            scope.launch {
                animate(current.top, top, animationSpec = tween(ReorderDuration)) { position, _ ->
                    current.top = position
                }
                drag = null
            }
        }
    }

    LaunchedEffect(moving, scrollState, edge, step) {
        val current = moving ?: return@LaunchedEffect
        // The pointer stays in viewport coordinates as the list moves beneath it.
        while (current.moving) {
            delay(AutoScrollIntervalMillis)
            val edgeSize = minOf(edge, scrollState.viewportSize / 2f)
            val y = current.pointerY
            if (edgeSize <= 0f || y.isNaN()) continue
            val direction = when {
                y < edgeSize && scrollState.canScrollBackward -> (y / edgeSize - 1).coerceAtLeast(-1f)
                y > scrollState.viewportSize - edgeSize && scrollState.canScrollForward ->
                    (1 - (scrollState.viewportSize - y) / edgeSize).coerceAtMost(1f)
                else -> 0f
            }
            if (direction != 0f) {
                val scrolled = scrollState.scrollBy(step * direction)
                moveDrag(current.id, scrolled)
            }
        }
    }

    val currentViewport by rememberUpdatedState(viewport)
    val outline = MaterialTheme.colorScheme.primary
    val slotColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = Modifier.fillMaxWidth().drawBehind {
            if (moving != null) {
                val top = previewTops.getValue(moving.id)
                val radius = CornerRadius(12.dp.toPx())
                val stroke = 2.dp.toPx()
                val inset = stroke / 2
                val slotSize = Size(size.width - stroke, moving.height - stroke)
                val offset = Offset(inset, top + inset)
                drawRoundRect(slotColor, offset, slotSize, radius)
                drawRoundRect(
                    outline,
                    offset,
                    slotSize,
                    radius,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
        },
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        exercises.forEachIndexed { index, exercise ->
            key(exercise.id) {
                val id = exercise.id
                val lifted = drag?.takeIf { it.id == id }
                val animatedTop by animateFloatAsState(
                    previewTops.getValue(id),
                    animationSpec = tween(ReorderDuration),
                    label = "exercise position",
                )
                var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val handle = Modifier.onGloballyPositioned { handleCoordinates = it }.semantics {
                    contentDescription = "Переместить «${exercise.name}»"
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Переместить выше") {
                            onMove(id, index - 1)
                            true
                        })
                        if (index < ids.lastIndex) add(CustomAccessibilityAction("Переместить ниже") {
                            onMove(id, index + 1)
                            true
                        })
                    }
                }.pointerInput(id) {
                    detectVerticalDragGestures(
                        onDragStart = { startDrag(id) },
                        onVerticalDrag = { change, amount ->
                            val handleLayout = handleCoordinates
                            val viewportLayout = currentViewport()
                            val current = drag
                            if (current?.id == id && handleLayout?.isAttached == true && viewportLayout?.isAttached == true) {
                                current.pointerY = viewportLayout.localPositionOf(handleLayout, change.position).y
                            }
                            moveDrag(id, amount)
                        },
                        onDragEnd = { finishDrag(id, true) },
                        onDragCancel = { finishDrag(id, false) },
                    )
                }
                val modifier = Modifier
                    .zIndex(if (lifted == null) 0f else 1f)
                    .graphicsLayer {
                        translationY = when {
                            lifted != null -> lifted.top - baseTops.getValue(id)
                            drag != null -> animatedTop - baseTops.getValue(id)
                            else -> 0f
                        }
                    }
                    .onSizeChanged { heights[id] = it.height }
                row(exercise, index, lifted != null, handle, modifier)
            }
        }
    }
}

private fun List<Long>.withMove(id: Long, destination: Int): List<Long> = toMutableList().apply {
    if (remove(id)) add(destination.coerceAtMost(size), id)
}

private fun rowTops(ids: List<Long>, heights: Map<Long, Int>, spacing: Int): Map<Long, Float> {
    var top = 0f
    return ids.associateWith { id ->
        val current = top
        top += (heights[id] ?: 0) + spacing
        current
    }
}

@Composable
private fun ExerciseEditor(
    exercise: ExerciseDraft,
    index: Int,
    expanded: Boolean,
    lifted: Boolean,
    showErrors: Boolean,
    onToggle: () -> Unit,
    onChange: ((ExerciseDraft) -> ExerciseDraft) -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    handle: Modifier,
    modifier: Modifier,
) {
    val suffix = if (index == 0) "" else "-${index + 1}"
    val setsError = showErrors && !exercise.sets.validRecommendation()
    val targetError = showErrors && !exercise.validTarget
    val target = formatRecommendation(exercise.sets.trim().toIntOrNull(), exercise.goal)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (lifted) colors.primaryContainer else colors.surfaceContainer,
        shape = RowShape,
        border = if (lifted) BorderStroke(2.dp, colors.primary) else null,
        shadowElevation = if (lifted) 8.dp else 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                        .clickable(role = Role.Button, onClick = onToggle)
                        .semantics { stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" }
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (target != null) Text(target, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!expanded && (setsError || targetError)) {
                        Text("Проверь цель", color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                DeleteIconButton("Удалить упражнение ${index + 1}", onClick = onDelete)
                ExerciseDragHandle(handle)
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (exercise.catalogExerciseId == null) {
                        TextButton(onClick = onReplace) { Text("Выбрать из каталога") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecommendationInput(
                            label = "Подходы",
                            value = exercise.sets,
                            onChange = { text -> onChange { it.copy(sets = text) } },
                            isError = setsError,
                            modifier = Modifier.weight(1f).testTag("recommendation-sets$suffix"),
                        )
                        RecommendationInput(
                            label = exercise.type.measureLabel,
                            value = exercise.target,
                            onChange = { text -> onChange { it.copy(target = text) } },
                            resetKey = exercise.type,
                            isError = targetError,
                            modifier = Modifier.weight(1f).testTag("recommendation-${exercise.type.measureTag}$suffix"),
                            decimal = exercise.type == ExerciseType.DISTANCE,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseDragHandle(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp).background(colors.primaryContainer, RoundedCornerShape(8.dp))) {
            for (column in 0..1) {
                for (row in 0..2) {
                    val x = center.x + (column - 0.5f) * 6.dp.toPx()
                    val y = center.y + (row - 1f) * 6.dp.toPx()
                    drawCircle(colors.primary, radius = 1.5.dp.toPx(), center = Offset(x, y))
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
    decimal: Boolean = false,
    resetKey: Any? = null,
) {
    DraftTextField(
        resetKey = resetKey,
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        isError = isError,
        supportingText = if (isError) ({
            Text(if (decimal) "Введи расстояние больше 0" else "Введи целое число от 1")
        }) else null,
        modifier = modifier,
    )
}
