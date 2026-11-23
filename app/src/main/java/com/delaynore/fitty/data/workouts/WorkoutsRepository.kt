package com.delaynore.fitty.data.workouts

import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import kotlinx.coroutines.flow.Flow

internal data class WorkoutSummary(
    val id: Long,
    val programName: String,
    val dayName: String,
    val finishedAtMillis: Long?,
)

internal data class LoggedSet(val position: Int, val result: SetResult, val id: Long = 0)

internal data class WorkoutExercise(
    val id: Long,
    val name: String,
    val sets: List<LoggedSet>,
    val recommendedSets: Int? = null,
    val goal: ExerciseGoal? = null,
    val removedPositions: Set<Int> = emptySet(),
    val catalogExerciseId: String? = null,
    val type: ExerciseType = ExerciseType.WEIGHT_AND_REPS,
    val hasEverLoggedSet: Boolean = false,
)

internal data class RemovedWorkoutSet(val set: LoggedSet?)

internal data class WorkoutSession(val summary: WorkoutSummary, val exercises: List<WorkoutExercise>)

internal data class InProgressWorkout(val id: Long, val dayName: String, val setCount: Int)

internal interface WorkoutsRepository {
    val inProgressWorkout: Flow<InProgressWorkout?>
    val history: Flow<List<WorkoutSummary>>
    suspend fun hasInProgressWorkout(): Boolean
    fun observeWorkout(id: Long): Flow<WorkoutSession?>
    suspend fun startWorkout(dayId: Long): Long?
    suspend fun replaceExercise(
        sessionId: Long,
        exerciseId: Long,
        sourceCatalogId: String,
        replacement: CatalogExercise,
    ): Boolean
    suspend fun logSet(sessionId: Long, expectedExercise: WorkoutExercise, set: LoggedSet): Boolean
    suspend fun updateInProgressSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean
    suspend fun removeSetPosition(
        sessionId: Long,
        expectedExercise: WorkoutExercise,
        position: Int,
        isRecorded: Boolean,
    ): RemovedWorkoutSet?
    suspend fun restoreSetPosition(sessionId: Long, exerciseId: Long, position: Int, set: LoggedSet?): Boolean
    suspend fun finishWorkout(sessionId: Long): Boolean
    suspend fun cancelWorkout(sessionId: Long): Boolean
    suspend fun updateCompletedSet(sessionId: Long, setId: Long, result: SetResult): Boolean
    suspend fun deleteCompletedSet(sessionId: Long, setId: Long): Boolean
    suspend fun deleteCompletedWorkout(sessionId: Long): Boolean
}
