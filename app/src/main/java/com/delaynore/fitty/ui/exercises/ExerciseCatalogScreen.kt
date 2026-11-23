package com.delaynore.fitty.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.data.exercises.BuiltInExercises
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun BrowseExerciseCatalog(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { BuiltInExercises[it] }
    val listState = rememberSaveableStateHolder()

    if (selected == null) {
        val results = remember(query) {
            val name = query.trim().lowercase().replace('ё', 'е')
            BuiltInExercises.all.filter { it.name.lowercase().replace('ё', 'е').contains(name) }
        }
        listState.SaveableStateProvider("list") {
            ExerciseCatalogContent(
                exercises = results,
                query = query,
                onQueryChange = { query = it },
                onOpenExercise = { selectedId = it.id },
                onBack = onBack,
            )
        }
    } else {
        val closeCard = { selectedId = null }
        BackHandler(onBack = closeCard)
        ExerciseCardContent(selected, onBack = closeCard)
    }
}

@Composable
internal fun ExerciseCatalogContent(
    exercises: List<CatalogExercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenExercise: (CatalogExercise) -> Unit,
    onBack: () -> Unit,
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            if (exercises.isEmpty()) {
                item { Text("Упражнения не найдены") }
            }
            items(exercises, key = { it.id }) { exercise ->
                Surface(
                    onClick = { onOpenExercise(exercise) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(exercise.type.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExerciseCardContent(exercise: CatalogExercise, onBack: () -> Unit) {
    ScreenColumn {
        PageHeader(exercise.name, onBack)
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
