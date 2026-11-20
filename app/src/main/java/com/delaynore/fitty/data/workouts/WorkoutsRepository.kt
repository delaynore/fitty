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

internal data class WorkoutExercise(val id: Long, val name: String, val sets: List<LoggedSet>)

internal data class WorkoutSession(val summary: WorkoutSummary, val exercises: List<WorkoutExercise>)

internal class WorkoutsRepository(private val database: FittyDatabase) {
    private val workouts = database.workouts()
    private val programs = database.programs()

    val inProgressWorkoutId = workouts.observeInProgressWorkoutId()

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
            WorkoutExerciseEntity(sessionId, position, exercise.name)
        })
        sessionId
    }

    suspend fun logSet(sessionId: Long, exerciseId: Long, weightKg: Double, repetitions: Int): Boolean {
        if (!weightKg.isFinite() || weightKg < 0 || repetitions <= 0) return false
        return database.withTransaction {
            if (workouts.inProgressWorkoutId() != sessionId || !workouts.exerciseBelongsToWorkout(exerciseId, sessionId)) {
                return@withTransaction false
            }
            workouts.insertSet(LoggedSetEntity(exerciseId, workouts.nextSetPosition(exerciseId), weightKg, repetitions))
            true
        }
    }

    suspend fun finishWorkout(sessionId: Long): Boolean = database.withTransaction {
        workouts.hasLoggedSets(sessionId) && workouts.finishWorkout(sessionId, System.currentTimeMillis()) == 1
    }

    private fun WorkoutSessionEntity.toSummary() = WorkoutSummary(id, programName, dayName, finishedAtMillis)
}
