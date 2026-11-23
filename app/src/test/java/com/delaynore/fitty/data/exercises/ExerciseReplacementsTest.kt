package com.delaynore.fitty.data.exercises

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
}
