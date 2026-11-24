package com.delaynore.fitty.data.exercises

import com.delaynore.fitty.domain.ExerciseReplacements
import com.delaynore.fitty.domain.replacementsFor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseReplacementsTest {
    @Test
    fun benchReplacementsKeepPairOrderAndNeverRepeatTheSourceOrSimilarExercises() {
        val source = "barbell-bench-press"
        val replacements = BuiltInExercises.all.reversed().replacementsFor(source)
        val similar = replacements.similar.map { it.id }
        val alternatives = replacements.sameMuscle.map { it.id }
        assertEquals(listOf("dumbbell-bench-press", "chest-press"), similar)
        assertTrue("push-up" in alternatives)
        assertFalse(source in similar + alternatives)
        assertTrue(alternatives.none { it in similar })
    }

    @Test
    fun unknownExerciseHasNoReplacements() {
        assertEquals(ExerciseReplacements(), BuiltInExercises.all.replacementsFor("missing"))
    }

    @Test
    fun sameMuscleUsesCanonicalOrderWithoutChangingSimilarPairPriority() {
        val source = CatalogExercise("barbell-bench-press", "Source", ExerciseType.REPS, primaryMuscle = MuscleGroup.CHEST)
        val similar = CatalogExercise("dumbbell-bench-press", "Similar", ExerciseType.REPS, primaryMuscle = MuscleGroup.CHEST)
        val customYo = CatalogExercise("custom:yo", "Ёж", ExerciseType.REPS, primaryMuscle = MuscleGroup.CHEST)
        val builtInYe = CatalogExercise("ye", "еж", ExerciseType.REPS, primaryMuscle = MuscleGroup.CHEST)
        val first = CatalogExercise("first", "Армейский жим", ExerciseType.REPS, primaryMuscle = MuscleGroup.CHEST)

        val replacements = listOf(customYo, similar, builtInYe, source, first).replacementsFor(source.id)

        assertEquals(listOf(similar.id), replacements.similar.map(CatalogExercise::id))
        assertEquals(listOf(first.id, builtInYe.id, customYo.id), replacements.sameMuscle.map(CatalogExercise::id))
    }
}
