package com.delaynore.fitty.domain

import com.delaynore.fitty.data.exercises.BuiltInExercises
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogExerciseOrderingTest {
    @Test
    fun nameNormalizationTrimsLowercasesAndTreatsYoAsYe() {
        assertEquals("еж лежа", normalizeExerciseName("  ЁЖ лЁжа  "))
    }

    @Test
    fun canonicalOrderBreaksNormalizedNameCollisionsDeterministically() {
        val exercises = listOf(
            exercise("custom:2", "Ёж", isCustom = true),
            exercise("built-in-2", "еж", isCustom = false),
            exercise("built-in-1", "Еж", isCustom = false),
            exercise("custom:1", "Ёж", isCustom = true),
        )

        assertEquals(
            listOf("built-in-1", "built-in-2", "custom:1", "custom:2"),
            exercises.sortedWith(catalogExerciseComparator).map(CatalogExercise::id),
        )
    }

    @Test
    fun builtInExercisesHaveUniqueIdsAndCanonicalOrder() {
        assertEquals(BuiltInExercises.all.size, BuiltInExercises.all.map(CatalogExercise::id).distinct().size)
        assertEquals(BuiltInExercises.all.sortedWith(catalogExerciseComparator), BuiltInExercises.all)
    }

    private fun exercise(id: String, name: String, isCustom: Boolean): CatalogExercise {
        val actualId = if (isCustom) id else id.removePrefix("custom:")
        return CatalogExercise(actualId, name, ExerciseType.REPS)
    }
}
