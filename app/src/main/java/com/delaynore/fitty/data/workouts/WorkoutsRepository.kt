package com.delaynore.fitty.data.workouts

import androidx.room.withTransaction
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.restoreGoal
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.LoggedSetEntity
import com.delaynore.fitty.data.local.RemovedSetPositionEntity
import com.delaynore.fitty.data.local.WorkoutExerciseEntity
import com.delaynore.fitty.data.local.WorkoutSessionEntity
import com.delaynore.fitty.data.local.orderedExercises
import kotlinx.coroutines.flow.map

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
)

internal data class RemovedWorkoutSet(val set: LoggedSet?)

internal data class WorkoutSession(val summary: WorkoutSummary, val exercises: List<WorkoutExercise>)

internal data class InProgressWorkout(val id: Long, val dayName: String, val setCount: Int)

internal class WorkoutsRepository(private val database: FittyDatabase) {
    private val workouts = database.workouts()
    private val programs = database.programs()

    val inProgressWorkout = workouts.observeInProgressWorkout().map { workout ->
        workout?.let { InProgressWorkout(it.id, it.dayName, it.setCount) }
    }

    val history = workouts.observeHistory().map { history -> history.map { it.toSummary() } }

    suspend fun hasInProgressWorkout(): Boolean = workouts.inProgressWorkoutId() != null

    fun observeWorkout(id: Long) = workouts.observeWorkout(id).map { stored ->
        stored?.let {
            WorkoutSession(
                summary = it.session.toSummary(),
                exercises = it.exercises.sortedBy { exercise -> exercise.exercise.position }.map { exercise ->
                    WorkoutExercise(
                        id = exercise.exercise.id,
                        name = exercise.exercise.name,
                        sets = exercise.sets.sortedBy { set -> set.position }
                            .map { set -> set.toLoggedSet(exercise.exercise.type) },
                        recommendedSets = exercise.exercise.recommendedSets,
                        goal = exercise.exercise.run {
                            type.restoreGoal(recommendedRepetitions, recommendedDurationSeconds, recommendedDistanceMeters)
                        },
                        removedPositions = exercise.removedPositions.toSet(),
                        catalogExerciseId = exercise.exercise.catalogExerciseId,
                        type = exercise.exercise.type,
                    )
                },
            )
        }
    }

    suspend fun startWorkout(dayId: Long): Long? = database.withTransaction {
        val activeWorkoutId = workouts.inProgressWorkoutId()
        if (activeWorkoutId != null) return@withTransaction activeWorkoutId
        val day = programs.trainingDay(dayId) ?: return@withTransaction null
        if (day.exercises.isEmpty()) return@withTransaction null

        val sessionId = workouts.insertSession(
            WorkoutSessionEntity(
                programName = programs.programName(day.day.programId),
                dayName = day.day.name,
                startedAtMillis = System.currentTimeMillis(),
            ),
        )
        workouts.insertExercises(day.orderedExercises().mapIndexed { position, exercise ->
            WorkoutExerciseEntity(
                sessionId = sessionId,
                position = position,
                name = exercise.name,
                recommendedSets = exercise.recommendedSets,
                recommendedRepetitions = exercise.recommendedRepetitions,
                recommendedDurationSeconds = exercise.recommendedDurationSeconds,
                recommendedDistanceMeters = exercise.recommendedDistanceMeters,
                catalogExerciseId = exercise.catalogExerciseId,
                type = exercise.type,
            )
        })
        sessionId
    }

    suspend fun logSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            if (inProgressExercise(sessionId, exerciseId)?.type != set.result.type ||
                workouts.isRemovedSetPosition(exerciseId, set.position) ||
                workouts.loggedSetAtPosition(exerciseId, set.position) != null
            ) {
                return@withTransaction false
            }
            workouts.insertSet(set.copy(id = 0).toEntity(exerciseId))
            true
        }
    }

    suspend fun updateInProgressSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            if (inProgressExercise(sessionId, exerciseId)?.type != set.result.type) return@withTransaction false
            val stored = workouts.loggedSetAtPosition(exerciseId, set.position) ?: return@withTransaction false
            workouts.updateSet(set.copy(id = stored.id).toEntity(exerciseId)) == 1
        }
    }

    suspend fun removeSetPosition(
        sessionId: Long,
        exerciseId: Long,
        position: Int,
        isRecorded: Boolean,
    ): RemovedWorkoutSet? = database.withTransaction {
        if (position <= 0) return@withTransaction null
        val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction null
        val set = workouts.loggedSetAtPosition(exerciseId, position)
        if ((set != null) != isRecorded) return@withTransaction null
        if (workouts.insertRemovedSetPosition(RemovedSetPositionEntity(exerciseId, position)) == -1L) {
            return@withTransaction null
        }
        if (set != null) workouts.deleteInProgressSet(set.id)
        RemovedWorkoutSet(set?.toLoggedSet(exercise.type))
    }

    suspend fun restoreSetPosition(sessionId: Long, exerciseId: Long, position: Int, set: LoggedSet?): Boolean =
        database.withTransaction {
            val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction false
            if (set != null && (set.position != position || !set.isValid() || set.result.type != exercise.type)) {
                return@withTransaction false
            }
            if (workouts.restoreSetPosition(exerciseId, position) != 1) return@withTransaction false
            if (set != null) workouts.insertSet(set.toEntity(exerciseId))
            true
        }

    suspend fun finishWorkout(sessionId: Long): Boolean = database.withTransaction {
        workouts.hasLoggedSets(sessionId) && workouts.finishWorkout(sessionId, System.currentTimeMillis()) == 1
    }

    suspend fun cancelWorkout(sessionId: Long): Boolean = workouts.cancelWorkout(sessionId) == 1

    suspend fun updateCompletedSet(sessionId: Long, setId: Long, result: SetResult): Boolean {
        if (!result.isValid()) return false
        return database.withTransaction {
            val stored = workouts.completedSet(sessionId, setId) ?: return@withTransaction false
            if (stored.type != result.type) return@withTransaction false
            val updated = LoggedSet(stored.set.position, result, stored.set.id)
            workouts.updateSet(updated.toEntity(stored.set.exerciseId)) == 1
        }
    }

    suspend fun deleteCompletedSet(sessionId: Long, setId: Long): Boolean =
        workouts.deleteCompletedSet(sessionId, setId) == 1

    suspend fun deleteCompletedWorkout(sessionId: Long): Boolean =
        workouts.deleteCompletedWorkout(sessionId) == 1

    private suspend fun inProgressExercise(sessionId: Long, exerciseId: Long): WorkoutExerciseEntity? =
        if (workouts.inProgressWorkoutId() == sessionId) workouts.workoutExercise(exerciseId, sessionId) else null

    private fun LoggedSet.isValid() = position > 0 && result.isValid()

    private fun LoggedSet.toEntity(exerciseId: Long): LoggedSetEntity = when (val value = result) {
        is SetResult.WeightAndReps -> LoggedSetEntity(exerciseId, position, value.weightKg, value.repetitions, id)
        is SetResult.Reps -> LoggedSetEntity(exerciseId, position, repetitions = value.repetitions, id = id)
        is SetResult.Duration -> LoggedSetEntity(exerciseId, position, id = id, durationSeconds = value.seconds)
        is SetResult.Distance -> LoggedSetEntity(exerciseId, position, id = id, distanceMeters = value.meters)
    }

    private fun LoggedSetEntity.toLoggedSet(type: ExerciseType): LoggedSet {
        val result = when (type) {
            ExerciseType.WEIGHT_AND_REPS -> SetResult.WeightAndReps(checkNotNull(weightKg), checkNotNull(repetitions))
            ExerciseType.REPS -> SetResult.Reps(checkNotNull(repetitions))
            ExerciseType.DURATION -> SetResult.Duration(checkNotNull(durationSeconds))
            ExerciseType.DISTANCE -> SetResult.Distance(checkNotNull(distanceMeters))
        }
        return LoggedSet(position, result, id)
    }

    private fun WorkoutSessionEntity.toSummary() = WorkoutSummary(id, programName, dayName, finishedAtMillis)
}
