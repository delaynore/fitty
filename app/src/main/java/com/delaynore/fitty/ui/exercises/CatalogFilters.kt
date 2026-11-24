package com.delaynore.fitty.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.Equipment
import com.delaynore.fitty.data.exercises.MuscleGroup
import com.delaynore.fitty.domain.CatalogFilters
import com.delaynore.fitty.domain.MuscleRole

internal val CatalogFiltersSaver = listSaver<CatalogFilters, String>(
    save = { filters ->
        listOf(filters.muscle?.name.orEmpty(), filters.role.name) + filters.availableEquipment.map { it.name }
    },
    restore = {
        CatalogFilters(
            muscle = it[0].takeIf(String::isNotEmpty)?.let(MuscleGroup::valueOf),
            role = MuscleRole.valueOf(it[1]),
            availableEquipment = it.drop(2).map(Equipment::valueOf).toSet(),
        )
    },
)

@Composable
internal fun CatalogFiltersDialog(
    filters: CatalogFilters,
    onApply: (CatalogFilters) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = CatalogFiltersSaver) { mutableStateOf(filters) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Фильтры каталога") },
        text = {
            CatalogFilterOptions(
                filters = draft,
                onMuscleChange = { draft = draft.copy(muscle = it) },
                onRoleChange = { draft = draft.copy(role = it) },
                onToggleEquipment = { draft = draft.toggleEquipment(it) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) { Text("Применить") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(CatalogFilters()) }) { Text("Сбросить") }
        },
    )
}

@Composable
private fun CatalogFilterOptions(
    filters: CatalogFilters,
    onMuscleChange: (MuscleGroup?) -> Unit,
    onRoleChange: (MuscleRole) -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Мышечная группа", style = MaterialTheme.typography.titleSmall)
        MuscleGroupMenu(filters.muscle, onMuscleChange)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MuscleRole.entries.forEach { role ->
                FilterChip(
                    selected = role == filters.role,
                    onClick = { onRoleChange(role) },
                    label = { Text(role.label) },
                )
            }
        }
        Text("Доступный инвентарь", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { equipment ->
                FilterChip(
                    selected = equipment in filters.availableEquipment,
                    onClick = { onToggleEquipment(equipment) },
                    label = { Text(equipment.label) },
                )
            }
        }
    }
}

@Composable
private fun MuscleGroupMenu(muscle: MuscleGroup?, onMuscleChange: (MuscleGroup?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(muscle?.label ?: "Любая группа") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Любая группа") }, onClick = {
                onMuscleChange(null)
                expanded = false
            })
            MuscleGroup.entries.forEach { group ->
                DropdownMenuItem(text = { Text(group.label) }, onClick = {
                    onMuscleChange(group)
                    expanded = false
                })
            }
        }
    }
}
