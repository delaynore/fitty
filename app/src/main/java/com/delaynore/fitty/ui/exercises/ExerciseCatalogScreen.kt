package com.delaynore.fitty.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun ExerciseCatalogScreen(
    viewModel: ExerciseCatalogViewModel,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onSelect: ((CatalogExercise) -> Unit)? = null,
    onCreate: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    ExerciseCatalogList(
        exercises = state.exercises,
        query = query,
        onQueryChange = { query = it },
        onOpenExercise = { onOpenExercise(it.id) },
        onBack = onBack,
        onSelectExercise = onSelect,
        onCreateExercise = onCreate,
    )
}

@Composable
private fun ExerciseCatalogList(
    exercises: List<CatalogExercise>?,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenExercise: (CatalogExercise) -> Unit,
    onBack: () -> Unit,
    onSelectExercise: ((CatalogExercise) -> Unit)?,
    onCreateExercise: ((String) -> Unit)?,
) {
    var filters by rememberSaveable(stateSaver = CatalogFilters.Saver) { mutableStateOf(CatalogFilters()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val results = remember(exercises, query, filters) {
        exercises?.let { filters.applyTo(it, query) }
    }

    ExerciseCatalogContent(
        exercises = results,
        listState = listState,
        query = query,
        onQueryChange = {
            listState.requestScrollToItem(0)
            onQueryChange(it)
        },
        activeFilters = filters.activeCount,
        onOpenFilters = {
            focusManager.clearFocus()
            showFilters = true
        },
        onOpenExercise = onOpenExercise,
        onBack = onBack,
        onSelectExercise = onSelectExercise,
        onCreateExercise = onCreateExercise?.takeIf { query.isNotBlank() }?.let { create ->
            {
                focusManager.clearFocus()
                create(query.trim())
            }
        },
    )

    if (showFilters) {
        CatalogFiltersDialog(
            filters = filters,
            onApply = {
                if (filters != it) listState.requestScrollToItem(0)
                filters = it
                showFilters = false
            },
            onCancel = { showFilters = false },
        )
    }
}

@Composable
private fun ExerciseCatalogContent(
    exercises: List<CatalogExercise>?,
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilters: Int,
    onOpenFilters: () -> Unit,
    onOpenExercise: (CatalogExercise) -> Unit,
    onBack: () -> Unit,
    onSelectExercise: ((CatalogExercise) -> Unit)?,
    onCreateExercise: (() -> Unit)?,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader("Каталог упражнений", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по названию") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        OutlinedButton(onClick = onOpenFilters) {
            Text(if (activeFilters == 0) "Фильтры" else "Фильтры ($activeFilters)")
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            if (exercises?.isEmpty() == true) {
                item { Text("Упражнения не найдены") }
                if (onCreateExercise != null) {
                    item { Button(onClick = onCreateExercise) { Text("Создать упражнение") } }
                }
            }
            items(exercises.orEmpty(), key = { it.id }) { exercise ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { onOpenExercise(exercise) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text(exercise.type.label, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (onSelectExercise != null) {
                            IconButton(
                                onClick = { onSelectExercise(exercise) },
                                modifier = Modifier.semantics { contentDescription = "Добавить ${exercise.name}" },
                            ) {
                                Text("＋", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExerciseCardContent(
    exercise: CatalogExercise,
    onBack: () -> Unit,
    selectionLabel: String,
    onSelect: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    ScreenColumn {
        PageHeader(exercise.name, onBack)
        if (onSelect != null) {
            Button(onClick = onSelect) { Text(selectionLabel) }
        }
        if (onEdit != null) {
            OutlinedButton(onClick = onEdit) { Text("Редактировать сведения") }
        }
        ExerciseAttribute("Тип записи", exercise.type.label)
        ExerciseAttribute(
            "Оборудование",
            exercise.equipment.joinToString(", ") { it.label.lowercase() }
                .replaceFirstChar { it.uppercase() }
                .ifEmpty { "Не указано" },
        )
        ExerciseAttribute("Основная группа мышц", exercise.primaryMuscle?.label ?: "Не указана")
        ExerciseAttribute(
            "Вспомогательные группы мышц",
            exercise.assistingMuscles.joinToString(", ") { it.label.lowercase() }
                .replaceFirstChar { it.uppercase() }
                .ifEmpty { "Нет" },
        )
        ExerciseAttribute("Сложность техники", exercise.difficulty?.label ?: "Не указана")
        exercise.technique?.let { ExerciseAttribute("Техника выполнения", it) }
    }
}

@Composable
private fun ExerciseAttribute(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
