package com.delaynore.fitty.domain

internal data class SavedRestTimer(
    val workoutId: Long,
    val deadlineMillis: Long,
    val bootCount: Int,
)

internal data class SavedSetTimer(
    val token: String,
    val workoutId: Long,
    val exerciseId: Long,
    val catalogExerciseId: String?,
    val position: Int,
    val startedAtMillis: Long,
    val goalAtMillis: Long?,
    val stoppedAtMillis: Long?,
    val bootCount: Int,
)

internal data class WorkoutTimersSnapshot(
    val restTimer: SavedRestTimer? = null,
    val setTimer: SavedSetTimer? = null,
)

internal interface WorkoutTimerStore {
    fun read(): WorkoutTimersSnapshot
    fun save(snapshot: WorkoutTimersSnapshot)
}
