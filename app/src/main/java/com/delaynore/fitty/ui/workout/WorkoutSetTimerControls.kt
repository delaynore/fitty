package com.delaynore.fitty.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun WorkoutSetTimerControls(
    timer: SetTimer?,
    otherExerciseName: String?,
    enabled: Boolean,
    elapsedMillis: (SetTimer) -> Long,
    onStart: () -> Unit,
    onStop: (String) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentElapsedMillis by rememberUpdatedState(elapsedMillis)
    var millis by remember(timer) { mutableLongStateOf(timer?.let(currentElapsedMillis) ?: 0L) }
    LaunchedEffect(timer, lifecycle) {
        if (timer == null || timer.stoppedAtMillis != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                millis = currentElapsedMillis(timer)
                delay(1_000L - millis % 1_000L)
            }
        }
    }
    SetTimerContent(
        elapsedSeconds = millis / 1_000L,
        otherExerciseName = otherExerciseName,
        enabled = enabled,
        active = timer != null,
        onStart = onStart,
        onStop = { timer?.let { onStop(it.token) } },
    )
}

@Composable
private fun SetTimerContent(
    elapsedSeconds: Long,
    otherExerciseName: String?,
    enabled: Boolean,
    active: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Замер подхода", style = MaterialTheme.typography.labelLarge)
            otherExerciseName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            "${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
        )
        if (active) {
            Button(onClick = onStop, enabled = enabled) { Text("Стоп") }
        } else {
            OutlinedButton(onClick = onStart, enabled = enabled) { Text("Старт") }
        }
    }
}
