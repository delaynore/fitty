package com.delaynore.fitty.data.workouts

import androidx.room.withTransaction
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.LoggedSetEntity
import com.delaynore.fitty.data.local.WorkoutExerciseEntity
import com.delaynore.fitty.data.local.WorkoutSessionEntity
import kotlinx.coroutines.flow.map

internal data class WorkoutSummary(
    val id: Long,
    val programName: String,
    val dayName: String,
    val finishedAtMillis: Long?,
)

internal data class LoggedSet(val position: Int, val weightKg: Double, val repetitions: Int)

internal data class WorkoutExercise(
    val id: Long,
    val name: String,
    val sets: List<LoggedSet>,
    val recommendedSets: Int? = null,
    val recommendedRepetitions: Int? = null,
)

internal data class WorkoutSession(val summary: WorkoutSummary, val exercises: List<WorkoutExercise>)

internal data class InProgressWorkout(val id: Long, val dayName: String, val setCount: Int)

internal class WorkoutsRepository(private val database: FittyDatabase) {
    private val workouts = database.workouts()
    private val programs = database.programs()

    val inProgressWorkout = workouts.observeInProgressWorkout().map { workout ->
        workout?.let { InProgressWorkout(it.id, it.dayName, it.setCount) }
    }

    val history = workouts.observeHistory().map { history -> history.map { it.toSummary() } }

    fun observeWorkout(id: Long) = workouts.observeWorkout(id).map { stored ->
        stored?.let {
            WorkoutSession(
                summary = it.session.toSummary(),
                exercises = it.exercises.sortedBy { exercise -> exercise.exercise.position }.map { exercise ->
                    WorkoutExercise(
                        id = exercise.exercise.id,
                        name = exercise.exercise.name,
                        sets = exercise.sets.sortedBy { set -> set.position }.map { set ->
                            LoggedSet(set.position, set.weightKg, set.repetitions)
                        },
                        recommendedSets = exercise.exercise.recommendedSets,
                        recommendedRepetitions = exercise.exercise.recommendedRepetitions,
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
            if (!isInProgressExercise(sessionId, exerciseId) || workouts.loggedSetAtPosition(exerciseId, set.position) != null) {
                return@withTransaction false
            }
            workouts.insertSet(LoggedSetEntity(exerciseId, set.position, set.weightKg, set.repetitions))
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

    suspend fun deleteInProgressSet(sessionId: Long, exerciseId: Long, position: Int): LoggedSet? = database.withTransaction {
        if (!isInProgressExercise(sessionId, exerciseId)) return@withTransaction null
        val set = workouts.loggedSetAtPosition(exerciseId, position) ?: return@withTransaction null
        if (workouts.deleteInProgressSet(set.id) != 1) return@withTransaction null
        LoggedSet(set.position, set.weightKg, set.repetitions)
    }

    suspend fun finishWorkout(sessionId: Long): Boolean = database.withTransaction {
        workouts.hasLoggedSets(sessionId) && workouts.finishWorkout(sessionId, System.currentTimeMillis()) == 1
    }

    suspend fun cancelWorkout(sessionId: Long): Boolean = workouts.cancelWorkout(sessionId) == 1

    private suspend fun isInProgressExercise(sessionId: Long, exerciseId: Long) =
        workouts.inProgressWorkoutId() == sessionId && workouts.exerciseBelongsToWorkout(exerciseId, sessionId)

    private fun LoggedSet.isValid() = position > 0 && weightKg.isFinite() && weightKg >= 0 && repetitions > 0

    private fun WorkoutSessionEntity.toSummary() = WorkoutSummary(id, programName, dayName, finishedAtMillis)
}
