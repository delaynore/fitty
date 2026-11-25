package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.domain.formatDecimal
import com.delaynore.fitty.domain.formatRecommendation
import com.delaynore.fitty.domain.formatResult
import com.delaynore.fitty.domain.inputText
import com.delaynore.fitty.domain.parseGoal
import com.delaynore.fitty.domain.parseWholeNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutFormattingTest {
    @Test
    fun decimalsUseCommaAndTrimOnlyTrailingZeroes() {
        assertEquals("0", formatDecimal(0.0))
        assertEquals("1,2345", formatDecimal(1.2345))
        assertEquals("15", formatDecimal(15.0))
    }

    @Test
    fun wholeNumbersTrimSpacesAndRejectOverflowOrNonDigits() {
        assertEquals(12, parseWholeNumber(" 12 "))
        assertNull(parseWholeNumber("2147483648"))
        assertNull(parseWholeNumber("1.0"))
        assertNull(parseWholeNumber("12a"))
    }

    @Test
    fun goalsExposeEditableInputText() {
        assertEquals("8", ExerciseGoal.Repetitions(8).inputText())
        assertEquals("30", ExerciseGoal.Duration(30).inputText())
        assertEquals("1,5", ExerciseGoal.Distance(1.5).inputText())
    }
    @Test
    fun setFieldsMatchEachExerciseTypeAndItsUnits() {
        val expectedLabels = mapOf(
            ExerciseType.WEIGHT_AND_REPS to listOf("Вес, кг", "Повторения"),
            ExerciseType.REPS to listOf("Повторения"),
            ExerciseType.DURATION to listOf("Время, с"),
            ExerciseType.DISTANCE to listOf("Дистанция, м"),
        )
        for ((type, labels) in expectedLabels) {
            val fieldLabels = buildList {
                if (type.hasWeightField) add(SET_WEIGHT_LABEL)
                add(type.measureLabel)
            }
            assertEquals(labels, fieldLabels)
        }
    }

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
        assertEquals("4 × 1,5 м", formatRecommendation(4, ExerciseGoal.Distance(1.5)))
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
