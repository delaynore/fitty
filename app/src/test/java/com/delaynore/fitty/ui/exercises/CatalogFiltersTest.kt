package com.delaynore.fitty.ui.exercises

import com.delaynore.fitty.data.exercises.BuiltInExercises
import com.delaynore.fitty.data.exercises.Equipment
import com.delaynore.fitty.data.exercises.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFiltersTest {
    private val benchPress = exercise("dumbbell-bench-press")
    private val shoulderPress = exercise("dumbbell-shoulder-press")
    private val row = exercise("dumbbell-row")
    private val curl = exercise("dumbbell-curl")
    private val pulldown = exercise("lat-pulldown")
    private val plank = exercise("plank")

    @Test
    fun allRequiredEquipmentMustBeAvailable() {
        val exercises = listOf(benchPress, shoulderPress)
        val dumbbells = CatalogFilters().toggleEquipment(Equipment.DUMBBELLS)
        assertEquals(listOf(shoulderPress), dumbbells.applyTo(exercises, ""))
        assertEquals(exercises, dumbbells.toggleEquipment(Equipment.BENCH).applyTo(exercises, ""))
    }

    @Test
    fun muscleRoleSelectsPrimaryOrAssistingMuscles() {
        val exercises = listOf(curl, pulldown)
        val biceps = CatalogFilters(muscle = MuscleGroup.BICEPS)
        assertEquals(listOf(curl), biceps.applyTo(exercises, ""))
        assertEquals(listOf(pulldown), biceps.copy(role = MuscleRole.ASSISTING).applyTo(exercises, ""))
    }

    @Test
    fun searchIgnoresCaseSurroundingWhitespaceAndNormalizesYo() {
        val exercises = listOf(benchPress, shoulderPress, row)
        assertEquals(listOf(benchPress), CatalogFilters().applyTo(exercises, "  ГАНТЕЛЕЙ ЛЕЖА  "))
    }

    @Test
    fun resultsMustMatchTheQueryMuscleAndEquipmentTogether() {
        val exercises = listOf(benchPress, shoulderPress, row)
        val filters = CatalogFilters(
            muscle = MuscleGroup.SHOULDERS,
            role = MuscleRole.ASSISTING,
            availableEquipment = setOf(Equipment.DUMBBELLS),
        )
        assertTrue(filters.applyTo(exercises, "жим гантелей").isEmpty())
        val withBench = filters.toggleEquipment(Equipment.BENCH)
        assertEquals(listOf(benchPress), withBench.applyTo(exercises, "жим гантелей"))
        assertTrue(withBench.applyTo(exercises, "тяга").isEmpty())
    }

    @Test
    fun selectingNoEquipmentReplacesTheEquipmentSelection() {
        val exercises = listOf(plank, shoulderPress)
        val dumbbells = CatalogFilters().toggleEquipment(Equipment.DUMBBELLS)
        val noEquipment = dumbbells.toggleEquipment(Equipment.NONE)
        assertEquals(listOf(plank), noEquipment.applyTo(exercises, ""))
        assertEquals(exercises, noEquipment.toggleEquipment(Equipment.NONE).applyTo(exercises, ""))
        assertEquals(dumbbells, noEquipment.toggleEquipment(Equipment.DUMBBELLS))
    }

    private fun exercise(id: String) = BuiltInExercises.all.single { it.id == id }
}
