package com.delaynore.fitty.domain

import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.WorkoutExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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

internal fun SetTimer.saved(bootCount: Int) = SavedSetTimer(
    token, workoutId, exerciseId, catalogExerciseId, position, startedAtMillis, goalAtMillis, stoppedAtMillis, bootCount,
)

internal fun restoreSetTimer(state: SavedSetTimer?, workoutId: Long, bootCount: Int, now: Long): SetTimer? {
    if (state == null || bootCount < 0 || state.bootCount != bootCount ||
        state.workoutId != workoutId || state.startedAtMillis > now
    ) return null
    return SetTimer(
        token = state.token,
        workoutId = workoutId,
        exerciseId = state.exerciseId,
        catalogExerciseId = state.catalogExerciseId,
        position = state.position,
        startedAtMillis = state.startedAtMillis,
        goalAtMillis = state.goalAtMillis,
        stoppedAtMillis = state.stoppedAtMillis,
    )
}

internal data class SetTimerSignal(val token: String, val workoutId: Long, val deadlineMillis: Long)

internal interface SetTimerSignalScheduler {
    val failures: Flow<SetTimerSignal> get() = emptyFlow()
    fun schedule(token: String, workoutId: Long, deadlineMillis: Long): Boolean
    fun cancel(token: String)
    fun cancelWorkout(workoutId: Long)
}
