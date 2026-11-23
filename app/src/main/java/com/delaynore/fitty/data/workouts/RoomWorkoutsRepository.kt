package com.delaynore.fitty.data.workouts

import androidx.room.withTransaction
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.restoreGoal
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.LoggedSetEntity
import com.delaynore.fitty.data.local.RemovedSetPositionEntity
import com.delaynore.fitty.data.local.WorkoutExerciseEntity
import com.delaynore.fitty.data.local.WorkoutSessionEntity
import com.delaynore.fitty.data.local.goal
import com.delaynore.fitty.data.local.orderedExercises
import kotlinx.coroutines.flow.map

internal class RoomWorkoutsRepository(private val database: FittyDatabase) : WorkoutsRepository {
    private val workouts = database.workouts()
    private val programs = database.programs()

    override val inProgressWorkout = workouts.observeInProgressWorkout().map { workout ->
        workout?.let { InProgressWorkout(it.id, it.dayName, it.setCount) }
    }

    override val history = workouts.observeHistory().map { history -> history.map { it.toSummary() } }

    override suspend fun hasInProgressWorkout(): Boolean = workouts.inProgressWorkoutId() != null

    override fun observeWorkout(id: Long) = workouts.observeWorkout(id).map { stored ->
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
                        goal = exercise.exercise.goal,
                        removedPositions = exercise.removedPositions.toSet(),
                        catalogExerciseId = exercise.exercise.catalogExerciseId,
                        type = exercise.exercise.type,
                        hasEverLoggedSet = exercise.exercise.hasEverLoggedSet,
                    )
                },
            )
        }
    }

    override suspend fun startWorkout(dayId: Long): Long? = database.withTransaction {
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
                catalogExerciseId = exercise.catalogExerciseId,
                type = exercise.type,
            ).withGoal(exercise.goal)
        })
        sessionId
    }

    override suspend fun replaceExercise(
        sessionId: Long,
        exerciseId: Long,
        sourceCatalogId: String,
        replacement: CatalogExercise,
    ): Boolean = database.withTransaction {
        val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction false
        if (exercise.hasEverLoggedSet || exercise.catalogExerciseId != sourceCatalogId ||
            replacement.id == sourceCatalogId
        ) return@withTransaction false

        val goal = exercise.goal.takeIf { exercise.type == replacement.type }
        val updated = exercise.copy(
            name = replacement.name,
            catalogExerciseId = replacement.id,
            type = replacement.type,
        ).withGoal(goal)
        if (workouts.updateExercise(updated) != 1) return@withTransaction false
        workouts.clearRemovedSetPositions(exerciseId)
        true
    }

    override suspend fun logSet(sessionId: Long, expectedExercise: WorkoutExercise, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            val exerciseId = expectedExercise.id
            val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction false
            if (exercise.type != set.result.type ||
                exercise.catalogExerciseId != expectedExercise.catalogExerciseId ||
                workouts.isRemovedSetPosition(exerciseId, set.position) ||
                workouts.loggedSetAtPosition(exerciseId, set.position) != null
            ) {
                return@withTransaction false
            }
            workouts.insertSet(set.copy(id = 0).toEntity(exerciseId))
            if (!exercise.hasEverLoggedSet) workouts.markSetLogged(exerciseId)
            true
        }
    }

    override suspend fun updateInProgressSet(sessionId: Long, exerciseId: Long, set: LoggedSet): Boolean {
        if (!set.isValid()) return false
        return database.withTransaction {
            if (inProgressExercise(sessionId, exerciseId)?.type != set.result.type) return@withTransaction false
            val stored = workouts.loggedSetAtPosition(exerciseId, set.position) ?: return@withTransaction false
            workouts.updateSet(set.copy(id = stored.id).toEntity(exerciseId)) == 1
        }
    }

    override suspend fun removeSetPosition(
        sessionId: Long,
        expectedExercise: WorkoutExercise,
        position: Int,
        isRecorded: Boolean,
    ): RemovedWorkoutSet? = database.withTransaction {
        if (position <= 0) return@withTransaction null
        val exerciseId = expectedExercise.id
        val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction null
        if (exercise.catalogExerciseId != expectedExercise.catalogExerciseId || exercise.type != expectedExercise.type) {
            return@withTransaction null
        }
        val set = workouts.loggedSetAtPosition(exerciseId, position)
        if ((set != null) != isRecorded) return@withTransaction null
        if (workouts.insertRemovedSetPosition(RemovedSetPositionEntity(exerciseId, position)) == -1L) {
            return@withTransaction null
        }
        if (set != null) workouts.deleteInProgressSet(set.id)
        RemovedWorkoutSet(set?.toLoggedSet(exercise.type))
    }

    override suspend fun restoreSetPosition(sessionId: Long, exerciseId: Long, position: Int, set: LoggedSet?): Boolean =
        database.withTransaction {
            val exercise = inProgressExercise(sessionId, exerciseId) ?: return@withTransaction false
            if (set != null && (set.position != position || !set.isValid() || set.result.type != exercise.type)) {
                return@withTransaction false
            }
            if (workouts.restoreSetPosition(exerciseId, position) != 1) return@withTransaction false
            if (set != null) {
                workouts.insertSet(set.toEntity(exerciseId))
                if (!exercise.hasEverLoggedSet) workouts.markSetLogged(exerciseId)
            }
            true
        }

    override suspend fun finishWorkout(sessionId: Long): Boolean = database.withTransaction {
        workouts.hasLoggedSets(sessionId) && workouts.finishWorkout(sessionId, System.currentTimeMillis()) == 1
    }

    override suspend fun cancelWorkout(sessionId: Long): Boolean = workouts.cancelWorkout(sessionId) == 1

    override suspend fun updateCompletedSet(sessionId: Long, setId: Long, result: SetResult): Boolean {
        if (!result.isValid()) return false
        return database.withTransaction {
            val stored = workouts.completedSet(sessionId, setId) ?: return@withTransaction false
            if (stored.type != result.type) return@withTransaction false
            val updated = LoggedSet(stored.set.position, result, stored.set.id)
            workouts.updateSet(updated.toEntity(stored.set.exerciseId)) == 1
        }
    }

    override suspend fun deleteCompletedSet(sessionId: Long, setId: Long): Boolean =
        workouts.deleteCompletedSet(sessionId, setId) == 1

    override suspend fun deleteCompletedWorkout(sessionId: Long): Boolean =
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

    private val WorkoutExerciseEntity.goal: ExerciseGoal?
        get() = type.restoreGoal(recommendedRepetitions, recommendedDurationSeconds, recommendedDistanceMeters)

    private fun WorkoutExerciseEntity.withGoal(goal: ExerciseGoal?) = copy(
        recommendedRepetitions = (goal as? ExerciseGoal.Repetitions)?.repetitions,
        recommendedDurationSeconds = (goal as? ExerciseGoal.Duration)?.seconds,
        recommendedDistanceMeters = (goal as? ExerciseGoal.Distance)?.meters,
    )

    private fun WorkoutSessionEntity.toSummary() = WorkoutSummary(id, programName, dayName, finishedAtMillis)
}
