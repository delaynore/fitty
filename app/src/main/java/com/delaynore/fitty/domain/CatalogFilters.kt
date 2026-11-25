package com.delaynore.fitty.domain

import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.Equipment
import com.delaynore.fitty.data.exercises.MuscleGroup

internal enum class MuscleRole(val label: String) {
    PRIMARY("Основная"), ASSISTING("Вспомогательная"),
}

internal data class CatalogFilters(
    val muscle: MuscleGroup? = null,
    val role: MuscleRole = MuscleRole.PRIMARY,
    val availableEquipment: Set<Equipment> = emptySet(),
) {
    val activeCount: Int get() = listOf(muscle != null, availableEquipment.isNotEmpty()).count { it }
    fun applyTo(exercises: List<CatalogExercise>, query: String): List<CatalogExercise> {
        val name = normalizeExerciseName(query)
        return exercises.filter { normalizeExerciseName(it.name).contains(name) && matches(it) }
    }
    private fun matches(exercise: CatalogExercise): Boolean {
        val matchesMuscle = muscle == null || when (role) {
            MuscleRole.PRIMARY -> exercise.primaryMuscle == muscle
            MuscleRole.ASSISTING -> muscle in exercise.assistingMuscles
        }
        if (!matchesMuscle) return false
        if (availableEquipment.isEmpty()) return true
        return exercise.equipment.isNotEmpty() && exercise.equipment.all { it == Equipment.NONE || it in availableEquipment }
    }
    fun toggleEquipment(equipment: Equipment): CatalogFilters = copy(availableEquipment = when {
        equipment in availableEquipment -> availableEquipment - equipment
        equipment == Equipment.NONE -> setOf(equipment)
        else -> availableEquipment - Equipment.NONE + equipment
    })
}
