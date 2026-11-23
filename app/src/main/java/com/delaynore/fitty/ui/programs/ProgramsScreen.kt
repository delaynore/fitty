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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramsContent(state, onBack, onCreate, onDetails, onSelect = { viewModel.selectProgram(it, onSelected) })
}

@Composable
private fun ProgramsContent(
    state: ProgramEditorUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onDetails: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    ScreenColumn {
        PageHeader("Мои программы", onBack)
        val entries = state.programs
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
                    onClick = { onSelect(program.id) },
                    enabled = !state.selecting,
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
        state.selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    DisposableEffect(viewModel, id) {
        viewModel.showProgram(id)
        onDispose { viewModel.showProgram(null) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramDetailsContent(state.details.takeIf { state.detailsId == id }, onBack, onEdit)
}

@Composable
private fun ProgramDetailsContent(
    program: TrainingProgram?,
    onBack: () -> Unit,
    onEdit: (TrainingProgram) -> Unit,
) {
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
