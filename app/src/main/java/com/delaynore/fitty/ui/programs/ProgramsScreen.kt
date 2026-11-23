package com.delaynore.fitty.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn
import com.delaynore.fitty.ui.formatRecommendation

@Composable
internal fun ProgramsScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onDetails: (Long) -> Unit,
    onSelected: () -> Unit,
) {
    val programs by viewModel.allPrograms.collectAsStateWithLifecycle(initialValue = null)
    ScreenColumn {
        PageHeader("Мои программы", onBack)
        val entries = programs
        if (entries == null) CircularProgressIndicator()
        else entries.forEach { program ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { onDetails(program.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(program.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
                IconButton(
                    onClick = { viewModel.selectProgram(program.id, onSelected) },
                    enabled = !viewModel.selecting,
                    modifier = Modifier.semantics {
                        contentDescription = "Выбрать программу «${program.name}»"
                        if (program.selected) stateDescription = "Выбрана"
                    },
                    colors = if (program.selected) {
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.outline),
                ) { FittyGlyph(FittyIcon.Check) }
            }
        }
        viewModel.selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Создать программу") }
    }
}

@Composable
internal fun ProgramDetailsScreen(
    id: Long,
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onEdit: (TrainingProgram) -> Unit,
) {
    val details = remember(id) { viewModel.observeProgram(id) }
    val program by details.collectAsStateWithLifecycle(initialValue = null)
    ScreenColumn {
        val current = program
        PageHeader(current?.name ?: "Программа", onBack)
        if (current == null) CircularProgressIndicator()
        else {
            current.days.forEach { day ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(day.name, style = MaterialTheme.typography.titleLarge)
                        day.exercises.forEach { exercise ->
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(exercise.name, Modifier.weight(1f))
                                formatRecommendation(exercise.recommendedSets, exercise.goal)?.let {
                                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Button(onClick = { onEdit(current) }, modifier = Modifier.fillMaxWidth()) { Text("Редактировать программу") }
        }
    }
}
