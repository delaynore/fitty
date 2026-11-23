package com.delaynore.fitty.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.workouts.InProgressWorkout
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.formatRecommendation

@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateProgram: () -> Unit,
    onPrograms: () -> Unit,
    onWorkout: (Long) -> Unit,
) {
    val state by viewModel.home.collectAsStateWithLifecycle()

    ScreenColumn {
        PageHeader("Fitty")
        val home = state
        val program = home?.program
        val inProgress = home?.inProgressWorkout
        if (inProgress != null) {
            InProgressWorkoutCard(inProgress, viewModel, onWorkout)
        }
        when {
            home == null -> CircularProgressIndicator()
            program == null -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Тренировочные программы", style = MaterialTheme.typography.titleLarge)
                    Text("Добавь тренировочный день и упражнение, чтобы начать тренировку.")
                    Button(onClick = onCreateProgram, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Создать программу")
                    }
                }
            }
            else -> {
                val selectedDay = program.days.firstOrNull { it.id == viewModel.selectedDayId } ?: program.days.firstOrNull()
                Surface(
                    onClick = onPrograms,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Мои программы", style = MaterialTheme.typography.labelLarge)
                            Text(program.name, style = MaterialTheme.typography.titleLarge)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Text("Тренировочный день", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(program.days, key = { it.id }) { day ->
                        FilterChip(
                            selected = day.id == selectedDay?.id,
                            onClick = { viewModel.selectDay(day.id) },
                            label = { Text(day.name) },
                        )
                    }
                }
                if (selectedDay != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Упражнения", style = MaterialTheme.typography.titleMedium)
                            selectedDay.exercises.forEachIndexed { index, exercise ->
                                if (index > 0) HorizontalDivider()
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(exercise.name, Modifier.weight(1f))
                                    formatRecommendation(exercise.recommendedSets, exercise.goal)?.let {
                                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (inProgress == null) {
                        Button(
                            onClick = { viewModel.startWorkout(onWorkout) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            enabled = !viewModel.starting && selectedDay.exercises.isNotEmpty(),
                        ) { Text("Начать тренировку") }
                    }
                }
            }
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InProgressWorkoutCard(workout: InProgressWorkout, viewModel: HomeViewModel, onWorkout: (Long) -> Unit) {
    var confirmCancel by remember(workout.id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Незавершённая тренировка", style = MaterialTheme.typography.labelLarge)
            Text(workout.dayName, style = MaterialTheme.typography.titleLarge)
            Text("Записано подходов: ${workout.setCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onWorkout(workout.id) },
                enabled = !viewModel.starting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Продолжить тренировку") }
            TextButton(
                onClick = { confirmCancel = true },
                enabled = !viewModel.starting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Отменить тренировку", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Отменить тренировку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${workout.dayName}. Записано подходов: ${workout.setCount}")
                    Text("Записанные подходы удалятся.")
                    viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelWorkout(workout.id) { confirmCancel = false } },
                    enabled = !viewModel.starting,
                ) { Text("Отменить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Оставить тренировку") } },
        )
    }
}
