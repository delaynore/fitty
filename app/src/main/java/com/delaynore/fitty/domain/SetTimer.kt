package com.delaynore.fitty.domain

import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.WorkoutExercise

internal data class SetTimer(
    val token: String,
    val workoutId: Long,
    val exerciseId: Long,
    val catalogExerciseId: String?,
    val position: Int,
    val startedAtMillis: Long,
    val goalAtMillis: Long?,
    val stoppedAtMillis: Long? = null,
) {
    fun matches(exercise: WorkoutExercise): Boolean =
        exerciseId == exercise.id && catalogExerciseId == exercise.catalogExerciseId && exercise.type == ExerciseType.DURATION
    fun elapsedMillis(now: Long): Long = ((stoppedAtMillis ?: now) - startedAtMillis).coerceAtLeast(0L)
}

internal data class SetTimerSignal(val token: String, val workoutId: Long, val deadlineMillis: Long)

internal interface SetTimerSignalScheduler {
    fun schedule(token: String, workoutId: Long, deadlineMillis: Long): Boolean
    fun cancel(token: String)
    fun cancelWorkout(workoutId: Long)
}
