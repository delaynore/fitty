package com.delaynore.fitty.ui.workout

import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.WorkoutExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTimerTest {
    private val timer = SetTimer("timer", 1, 2, "plank", 1, 1000, null)

    @Test
    fun elapsedTimeStaysNonNegativeAndFreezesOnStop() {
        assertEquals(0L, timer.elapsedMillis(999))
        assertEquals(499L, timer.elapsedMillis(1499))
        assertEquals(499L, timer.copy(stoppedAtMillis = 1499).elapsedMillis(3000))
    }

    @Test
    fun timerOnlyMatchesItsOriginalDurationExercise() {
        val plank = WorkoutExercise(2, "Планка", emptyList(), catalogExerciseId = "plank", type = ExerciseType.DURATION)
        assertTrue(timer.matches(plank))
        assertFalse(timer.matches(plank.copy(id = 3)))
        assertFalse(timer.matches(plank.copy(catalogExerciseId = "other")))
        assertFalse(timer.matches(plank.copy(type = ExerciseType.REPS)))
    }
}
