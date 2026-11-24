package com.delaynore.fitty.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.domain.ExerciseReplacements

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseReplacementSheet(
    options: ExerciseReplacements?,
    saving: Boolean,
    error: String?,
    onSelect: (CatalogExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Замена на сегодня",
            Modifier.padding(horizontal = 20.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
        when {
            options == null -> CircularProgressIndicator(Modifier.padding(20.dp))
            options.similar.isEmpty() && options.sameMuscle.isEmpty() ->
                Text("В каталоге пока нет вариантов замены", Modifier.padding(20.dp))
            else -> LazyColumn(
                Modifier.heightIn(max = 420.dp).testTag("workout-replacement-list"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (options.similar.isNotEmpty()) {
                    item { ReplacementHeading("Похожие движения") }
                    items(options.similar, key = CatalogExercise::id) { exercise ->
                        ReplacementRow(exercise, saving, onSelect)
                    }
                }
                if (options.sameMuscle.isNotEmpty()) {
                    item { ReplacementHeading("Та же основная группа мышц") }
                    items(options.sameMuscle, key = CatalogExercise::id) { exercise ->
                        ReplacementRow(exercise, saving, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplacementHeading(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ReplacementRow(exercise: CatalogExercise, saving: Boolean, onSelect: (CatalogExercise) -> Unit) {
    Surface(
        onClick = { onSelect(exercise) },
        enabled = !saving,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            val equipment = exercise.equipment.joinToString(", ") { it.label }
            val details = if (equipment.isBlank()) exercise.type.label else "${exercise.type.label} · $equipment"
            Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
