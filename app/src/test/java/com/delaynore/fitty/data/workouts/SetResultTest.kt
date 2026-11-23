package com.delaynore.fitty.data.workouts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetResultTest {
    @Test
    fun recordingZeroIsAllowedForElapsedTimeAndWeight() {
        assertTrue(SetResult.Duration(0).isValid())
        assertTrue(SetResult.WeightAndReps(0.0, 1).isValid())
        assertFalse(SetResult.Duration(-1).isValid())
        assertFalse(SetResult.Reps(0).isValid())
        assertFalse(SetResult.WeightAndReps(10.0, 0).isValid())
        assertFalse(SetResult.Distance(0.0).isValid())
    }

    @Test
    fun weightAndDistanceRejectNegativeAndNonFiniteValues() {
        for (value in listOf(-0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(SetResult.WeightAndReps(value, 8).isValid())
            assertFalse(SetResult.Distance(value).isValid())
        }
        assertTrue(SetResult.Distance(0.5).isValid())
    }
}
