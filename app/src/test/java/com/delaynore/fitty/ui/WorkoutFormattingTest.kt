package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.SetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutFormattingTest {
    @Test
    fun resultsShowTheirUnitAndTrimTrailingZeroes() {
        assertEquals("22,5 кг × 8", SetResult.WeightAndReps(22.50, 8).formatResult())
        assertEquals("8 повт.", SetResult.Reps(8).formatResult())
        assertEquals("0 с", SetResult.Duration(0).formatResult())
        assertEquals("15 м", SetResult.Distance(15.0).formatResult())
    }

    @Test
    fun recommendationsCanOmitTheGoalOrSetCount() {
        assertNull(formatRecommendation(null, null))
        assertEquals("3 подх.", formatRecommendation(3, null))
        assertEquals("8 повт.", formatRecommendation(null, ExerciseGoal.Repetitions(8)))
        assertEquals("3 × 8", formatRecommendation(3, ExerciseGoal.Repetitions(8)))
        assertEquals("2 × 30 с", formatRecommendation(2, ExerciseGoal.Duration(30)))
    }

    @Test
    fun decimalDistanceAcceptsEitherSeparatorAndDurationRequiresWholePositiveSeconds() {
        for (text in listOf("1,5", "1.5")) {
            assertEquals(ExerciseGoal.Distance(1.5), ExerciseType.DISTANCE.parseGoal(text))
            assertNull(ExerciseType.DURATION.parseGoal(text))
        }
        assertNull(ExerciseType.DURATION.parseGoal("0"))
        assertEquals(ExerciseGoal.Duration(30), ExerciseType.DURATION.parseGoal(" 30 "))
    }
}
