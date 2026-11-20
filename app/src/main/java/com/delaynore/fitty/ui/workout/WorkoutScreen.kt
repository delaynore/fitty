package com.delaynore.fitty.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.formatWeight

@Composable
internal fun WorkoutScreen(id: Long, viewModel: WorkoutViewModel, onBack: () -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(id) { viewModel.open(id) }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val workout = session?.takeIf { it.summary.id == id }
    val exercise = workout?.exercises?.firstOrNull()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenColumn(Modifier.weight(1f)) {
            PageHeader(workout?.summary?.dayName ?: "Тренировка", onBack)
            if (exercise == null) {
                CircularProgressIndicator()
            } else {
                Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
                SetHeadings()
                val setsByPosition = exercise.sets.associateBy { it.position }
                val nextPosition = (exercise.sets.lastOrNull()?.position ?: 0) + 1
                for (position in 1..maxOf(4, nextPosition - 1)) {
                    val logged = setsByPosition[position]
                    if (logged == null) {
                        DraftSetRow(position, active = position == nextPosition, viewModel) {
                            focus.clearFocus()
                            viewModel.logSet(id, exercise.id)
                        }
                    } else {
                        LoggedSetRow(logged)
                    }
                }
                viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        Button(
            onClick = {
                focus.clearFocus()
                viewModel.finish(id, onFinished)
            },
            enabled = exercise?.sets?.isNotEmpty() == true && !viewModel.saving,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Завершить") }
    }
}

@Composable
private fun SetHeadings() {
    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("№", Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Вес, кг", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Повторения", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun DraftSetRow(position: Int, active: Boolean, viewModel: WorkoutViewModel, onLog: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(position.toString(), Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = if (active) viewModel.weight else "",
            onValueChange = viewModel::changeWeight,
            enabled = active && !viewModel.saving,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag("workout-weight-$position").semantics { contentDescription = "Вес, подход $position" },
        )
        OutlinedTextField(
            value = if (active) viewModel.repetitions else "",
            onValueChange = viewModel::changeRepetitions,
            enabled = active && !viewModel.saving,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag("workout-repetitions-$position").semantics { contentDescription = "Повторения, подход $position" },
        )
        FilledTonalIconButton(
            onClick = onLog,
            enabled = active && viewModel.canLog,
            modifier = Modifier.width(48.dp).semantics { contentDescription = "Записать подход $position" },
        ) { FittyGlyph(FittyIcon.Check) }
    }
}

@Composable
private fun LoggedSetRow(set: LoggedSet) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(set.position.toString(), Modifier.width(22.dp))
            Text(formatWeight(set.weightKg), Modifier.weight(1f).testTag("workout-logged-weight-${set.position}"))
            Text(set.repetitions.toString(), Modifier.weight(1f).testTag("workout-logged-repetitions-${set.position}"))
            FittyGlyph(FittyIcon.Check)
        }
    }
}
