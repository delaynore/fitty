package com.delaynore.fitty.data.workouts

import androidx.room.withTransaction
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.LoggedSetEntity
import com.delaynore.fitty.data.local.RemovedSetPositionEntity
import com.delaynore.fitty.data.local.WorkoutExerciseEntity
import com.delaynore.fitty.data.local.WorkoutSessionEntity
import kotlinx.coroutines.flow.map

internal data class WorkoutSummary(
    val id: Long,
    val programName: String,
    val dayName: String,
    val finishedAtMillis: Long?,
)

internal data class LoggedSet(val position: Int, val weightKg: Double, val repetitions: Int, val id: Long = 0)

internal data class WorkoutExercise(
    val id: Long,
    val name: String,
    val sets: List<LoggedSet>,
    val recommendedSets: Int? = null,
    val recommendedRepetitions: Int? = null,
    val removedPositions: Set<Int> = emptySet(),
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
                        sets = exercise.sets.sortedBy { set -> set.position }.map { set -> set.toLoggedSet() },
                        recommendedSets = exercise.exercise.recommendedSets,
                        recommendedRepetitions = exercise.exercise.recommendedRepetitions,
                        removedPositions = exercise.removedPositions.toSet(),
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
        workouts.insertExercises(day.exercises.sortedBy { it.id }.mapIndexed { position, exercise ->
            WorkoutExerciseEntity(
                sessionId = sessionId,
                position = position,
                name = exercise.name,
                recommendedSets = exercise.recommendedSets,
                recommendedRepetitions = exercise.recommendedRepetitions,
            )
        })
        sessionId
    }

    suspend fun logSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            if (!isInProgressExercise(sessionId, exerciseId) ||
                workouts.isRemovedSetPosition(exerciseId, set.position) ||
                workouts.loggedSetAtPosition(exerciseId, set.position) != null
            ) {
                return@withTransaction false
            }
            workouts.insertSet(LoggedSetEntity(exerciseId, set.position, set.weightKg, set.repetitions, id = set.id))
            true
        }
    }

    suspend fun updateInProgressSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            isInProgressExercise(sessionId, exerciseId) &&
                workouts.updateInProgressSet(exerciseId, set.position, set.weightKg, set.repetitions) == 1
        }
    }

    suspend fun removeSetPosition(
        sessionId: Long,
        exerciseId: Long,
        position: Int,
        isRecorded: Boolean,
    ): RemovedWorkoutSet? = database.withTransaction {
        if (position <= 0 || !isInProgressExercise(sessionId, exerciseId)) return@withTransaction null
        val set = workouts.loggedSetAtPosition(exerciseId, position)
        if ((set != null) != isRecorded) return@withTransaction null
        if (workouts.insertRemovedSetPosition(RemovedSetPositionEntity(exerciseId, position)) == -1L) {
            return@withTransaction null
        }
        if (set != null) workouts.deleteInProgressSet(set.id)
        RemovedWorkoutSet(set?.toLoggedSet())
    }

    suspend fun restoreSetPosition(sessionId: Long, exerciseId: Long, position: Int, set: LoggedSet?): Boolean =
        database.withTransaction {
            if (set != null && (set.position != position || !set.isValid())) return@withTransaction false
            if (!isInProgressExercise(sessionId, exerciseId) || workouts.restoreSetPosition(exerciseId, position) != 1) {
                return@withTransaction false
            }
            if (set != null) workouts.insertSet(LoggedSetEntity(exerciseId, position, set.weightKg, set.repetitions, id = set.id))
            true
        }

    suspend fun finishWorkout(sessionId: Long): Boolean = database.withTransaction {
        workouts.hasLoggedSets(sessionId) && workouts.finishWorkout(sessionId, System.currentTimeMillis()) == 1
    }

    suspend fun cancelWorkout(sessionId: Long): Boolean = workouts.cancelWorkout(sessionId) == 1

    suspend fun updateCompletedSet(sessionId: Long, setId: Long, weightKg: Double, repetitions: Int): Boolean {
        if (!weightKg.isFinite() || weightKg < 0 || repetitions <= 0) return false
        return workouts.updateCompletedSet(sessionId, setId, weightKg, repetitions) == 1
    }

    suspend fun deleteCompletedSet(sessionId: Long, setId: Long): Boolean =
        workouts.deleteCompletedSet(sessionId, setId) == 1

    suspend fun deleteCompletedWorkout(sessionId: Long): Boolean =
        workouts.deleteCompletedWorkout(sessionId) == 1

    private suspend fun isInProgressExercise(sessionId: Long, exerciseId: Long) =
        workouts.inProgressWorkoutId() == sessionId && workouts.exerciseBelongsToWorkout(exerciseId, sessionId)

    private fun LoggedSet.isValid() = position > 0 && weightKg.isFinite() && weightKg >= 0 && repetitions > 0

    private fun LoggedSetEntity.toLoggedSet() = LoggedSet(position, weightKg, repetitions, id)

    private fun WorkoutSessionEntity.toSummary() = WorkoutSummary(id, programName, dayName, finishedAtMillis)
}
