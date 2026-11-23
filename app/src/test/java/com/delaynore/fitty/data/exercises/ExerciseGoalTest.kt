package com.delaynore.fitty.data.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExerciseGoalTest {
    @Test
    fun targetsUseTheMeasureOfTheExercise() {
        for (type in ExerciseType.entries) {
            assertEquals(type in listOf(ExerciseType.REPS, ExerciseType.WEIGHT_AND_REPS), ExerciseGoal.Repetitions(8).isValidFor(type))
            assertEquals(type == ExerciseType.DURATION, ExerciseGoal.Duration(30).isValidFor(type))
            assertEquals(type == ExerciseType.DISTANCE, ExerciseGoal.Distance(1.5).isValidFor(type))
        }
    }

    @Test
    fun targetsMustBePositiveAndFinite() {
        assertFalse(ExerciseGoal.Duration(0).isValidFor(ExerciseType.DURATION))
        assertFalse(ExerciseGoal.Repetitions(-1).isValidFor(ExerciseType.REPS))
        for (meters in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(ExerciseGoal.Distance(meters).isValidFor(ExerciseType.DISTANCE))
        }
    }
}
