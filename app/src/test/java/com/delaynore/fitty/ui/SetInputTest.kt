package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputErrors
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.toInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SetInputTest {
    @Test
    fun acceptsEachExerciseTypeAndBothDecimalSeparators() {
        assertEquals(SetResult.WeightAndReps(62.5, 8), SetInput(ExerciseType.WEIGHT_AND_REPS, "62,5", "8").toLoggedSet(1)?.result)
        assertEquals(SetResult.WeightAndReps(62.5, 8), SetInput(ExerciseType.WEIGHT_AND_REPS, "62.5", "8").toLoggedSet(1)?.result)
        assertEquals(SetResult.Reps(8), SetInput(ExerciseType.REPS, measure = "8").toLoggedSet(1)?.result)
        assertEquals(SetResult.Duration(0), SetInput(ExerciseType.DURATION, measure = "0").toLoggedSet(1)?.result)
        assertEquals(SetResult.Distance(1.5), SetInput(ExerciseType.DISTANCE, measure = "1,5").toLoggedSet(1)?.result)
    }

    @Test
    fun limitsEditedWeightToFourDecimalPlacesButPreservesLegacyPrecisionUntilEdit() {
        val legacy = SetResult.WeightAndReps(1.23456, 8).toInput()

        assertNotNull(legacy.toLoggedSet(1))
        assertNull(legacy.change(SetInputField.Weight, "1.23456").toLoggedSet(1))
        assertNotNull(legacy.change(SetInputField.Weight, "1.2345").toLoggedSet(1))
    }

    @Test
    fun enforcesZeroAndPositiveBoundaries() {
        assertNotNull(SetInput(ExerciseType.WEIGHT_AND_REPS, "0", "1").toLoggedSet(1))
        assertNotNull(SetInput(ExerciseType.DURATION, measure = "0").toLoggedSet(1))
        assertNull(SetInput(ExerciseType.REPS, measure = "0").toLoggedSet(1))
        assertNull(SetInput(ExerciseType.DISTANCE, measure = "0").toLoggedSet(1))
    }

    @Test
    fun revalidatesChangedFieldsAfterValidation() {
        val validated = SetInput(ExerciseType.WEIGHT_AND_REPS).validate()
        assertNotNull(validated.errors?.weight)
        assertNotNull(validated.errors?.measure)

        val valid = validated.change(SetInputField.Weight, "20").change(SetInputField.Measure, "5")
        assertEquals(SetInputErrors(), valid.errors)
    }

    @Test
    fun formatsDraftsAndRoundTripsResults() {
        assertEquals("62,5 кг × 8", SetInput(ExerciseType.WEIGHT_AND_REPS, "62,5", "8").formatDraft())
        assertEquals("− повт.", SetInput(ExerciseType.REPS).formatDraft())
        val results = listOf(
            SetResult.WeightAndReps(62.5, 8),
            SetResult.Reps(8),
            SetResult.Duration(0),
            SetResult.Distance(1.5),
        )
        results.forEach { result -> assertEquals(result, result.toInput().toLoggedSet(1)?.result) }
    }
}
