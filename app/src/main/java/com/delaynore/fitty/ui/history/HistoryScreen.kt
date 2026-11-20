package com.delaynore.fitty.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.formatWeight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HistoryDateFormat = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("ru"))

private fun workoutDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(HistoryDateFormat)

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
                            Text(workoutDate(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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

    ScreenColumn {
        val workout = details
        PageHeader(workout?.summary?.dayName ?: "Тренировка", onBack)
        if (workout == null) {
            CircularProgressIndicator()
        } else {
            workout.summary.finishedAtMillis?.let {
                Text(workoutDate(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
            Text(workout.summary.programName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            workout.exercises.filter { it.sets.isNotEmpty() }.forEach { exercise ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                        exercise.sets.forEach { set ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Подход ${set.position}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatWeight(set.weightKg)} кг × ${set.repetitions}")
                            }
                        }
                    }
                }
            }
        }
    }
}
