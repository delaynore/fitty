package com.delaynore.fitty.data.workouts

import com.delaynore.fitty.data.exercises.CatalogExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeWorkoutsRepository(
    private val trainingDays: Map<Long, WorkoutSession> = emptyMap(),
) : WorkoutsRepository {
    private val sessions = MutableStateFlow(emptyMap<Long, WorkoutSession>())
    private var nextSetId = 1L

    private fun activeSession() = sessions.value.values.firstOrNull { it.summary.finishedAtMillis == null }

    override val inProgressWorkout = sessions.map { saved ->
        saved.values.firstOrNull { it.summary.finishedAtMillis == null }?.let {
            InProgressWorkout(it.summary.id, it.summary.dayName, it.exercises.sumOf { exercise -> exercise.sets.size })
        }
    }
    override val history = sessions.map { saved -> saved.values.map { it.summary }.filter { it.finishedAtMillis != null } }
    override fun observeWorkout(id: Long) = sessions.map { it[id] }
    override suspend fun hasInProgressWorkout() = activeSession() != null

    override suspend fun startWorkout(dayId: Long): Long? {
        activeSession()?.let { return it.summary.id }
        val workout = trainingDays[dayId] ?: return null
        val id = (sessions.value.keys.maxOrNull() ?: 0L) + 1
        sessions.value += id to workout.copy(summary = workout.summary.copy(id = id, finishedAtMillis = null))
        return id
    }

    private fun editExercise(sessionId: Long, exerciseId: Long, edit: (WorkoutExercise) -> WorkoutExercise): Boolean {
        val session = sessions.value[sessionId] ?: return false
        if (session.exercises.none { it.id == exerciseId }) return false
        sessions.value += sessionId to session.copy(exercises = session.exercises.map { if (it.id == exerciseId) edit(it) else it })
        return true
    }

    override suspend fun replaceExercise(sessionId: Long, exerciseId: Long, sourceCatalogId: String, replacement: CatalogExercise) =
        editExercise(sessionId, exerciseId) { exercise ->
            exercise.copy(
                name = replacement.name,
                catalogExerciseId = replacement.id,
                type = replacement.type,
                goal = exercise.goal.takeIf { exercise.type == replacement.type },
                removedPositions = emptySet(),
            )
        }

    override suspend fun logSet(sessionId: Long, expectedExercise: WorkoutExercise, set: LoggedSet) =
        editExercise(sessionId, expectedExercise.id) { it.copy(sets = it.sets + set.copy(id = nextSetId++), hasEverLoggedSet = true) }

    override suspend fun updateInProgressSet(sessionId: Long, exerciseId: Long, set: LoggedSet) =
        editExercise(sessionId, exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.position == set.position) set.copy(id = it.id) else it })
        }

    override suspend fun removeSetPosition(sessionId: Long, expectedExercise: WorkoutExercise, position: Int, isRecorded: Boolean): RemovedWorkoutSet? {
        val exercise = sessions.value[sessionId]?.exercises?.find { it.id == expectedExercise.id } ?: return null
        val set = exercise.sets.find { it.position == position }
        editExercise(sessionId, exercise.id) {
            it.copy(sets = it.sets.filterNot { row -> row.position == position }, removedPositions = it.removedPositions + position)
        }
        return RemovedWorkoutSet(set)
    }

    override suspend fun restoreSetPosition(sessionId: Long, exerciseId: Long, position: Int, set: LoggedSet?) =
        editExercise(sessionId, exerciseId) {
            it.copy(
                removedPositions = it.removedPositions - position,
                sets = (it.sets + listOfNotNull(set)).sortedBy(LoggedSet::position),
            )
        }

    override suspend fun finishWorkout(sessionId: Long): Boolean {
        val session = sessions.value[sessionId] ?: return false
        sessions.value += sessionId to session.copy(summary = session.summary.copy(finishedAtMillis = 1L))
        return true
    }

    override suspend fun cancelWorkout(sessionId: Long): Boolean {
        if (activeSession()?.summary?.id != sessionId) return false
        sessions.value -= sessionId
        return true
    }

    private fun editCompletedSet(sessionId: Long, setId: Long, edit: (LoggedSet) -> LoggedSet?): Boolean {
        val exercise = sessions.value[sessionId]?.exercises?.find { it.sets.any { set -> set.id == setId } } ?: return false
        return editExercise(sessionId, exercise.id) { it.copy(sets = it.sets.mapNotNull { set -> if (set.id == setId) edit(set) else set }) }
    }

    override suspend fun updateCompletedSet(sessionId: Long, setId: Long, result: SetResult) =
        editCompletedSet(sessionId, setId) { it.copy(result = result) }

    override suspend fun deleteCompletedSet(sessionId: Long, setId: Long) = editCompletedSet(sessionId, setId) { null }

    override suspend fun deleteCompletedWorkout(sessionId: Long): Boolean {
        if (sessions.value[sessionId]?.summary?.finishedAtMillis == null) return false
        sessions.value -= sessionId
        return true
    }
}
