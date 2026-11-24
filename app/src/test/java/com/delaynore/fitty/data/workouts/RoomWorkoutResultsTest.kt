package com.delaynore.fitty.data.workouts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkoutResultsTest {
    @Test
    fun controlledClockRecordsExactTimesAndOrdersHistoryByFinishTime() = withWorkoutDatabase {
        open { database ->
            val day = saveDays(database, listOf(trainingExercise(bench))).single()
            val times = ArrayDeque(listOf(1_000L, 4_000L, 2_000L, 3_000L))
            val repository = RoomWorkoutsRepository(database, nowMillis = { times.removeFirst() })

            val first = requireNotNull(repository.startWorkout(day.id))
            assertTrue(repository.logSet(first, repository.workout(first).exercises.single(), loggedSet()))
            assertTrue(repository.finishWorkout(first))
            val second = requireNotNull(repository.startWorkout(day.id))
            assertTrue(repository.logSet(second, repository.workout(second).exercises.single(), loggedSet()))
            assertTrue(repository.finishWorkout(second))

            assertEquals(1_000L to 4_000L, database.sessionTimes(first))
            assertEquals(2_000L to 3_000L, database.sessionTimes(second))
            assertEquals(listOf(first, second), repository.history.first().map { it.id })
        }
    }

    @Test
    fun theSameExerciseSnapshotsTheGoalOfEachSelectedDay() = withWorkoutDatabase {
        val id = open { database ->
            val days = saveDays(
                database,
                listOf(trainingExercise(plank, ExerciseGoal.Duration(40))),
                listOf(trainingExercise(plank, ExerciseGoal.Duration(60))),
            )
            val repository = RoomWorkoutsRepository(database)
            val first = requireNotNull(repository.startWorkout(days.first().id))
            assertEquals(ExerciseGoal.Duration(40), repository.workout(first).exercises.single().goal)
            assertTrue(repository.cancelWorkout(first))
            requireNotNull(repository.startWorkout(days.last().id))
        }

        open { database ->
            val second = RoomWorkoutsRepository(database).workout(id)
            assertEquals("Day 2", second.summary.dayName)
            assertEquals(plank.id, second.exercises.single().catalogExerciseId)
            assertEquals(ExerciseGoal.Duration(60), second.exercises.single().goal)
        }
    }

    @Test
    fun historyEditsReopenWithTheOriginalResultTypesAndLastSetIsProtected() = withWorkoutDatabase {
        val initial = listOf(SetResult.WeightAndReps(62.5, 8), SetResult.Reps(5), SetResult.Duration(47), SetResult.Distance(1250.5))
        val edited = listOf(SetResult.WeightAndReps(65.0, 8), SetResult.Reps(6), SetResult.Duration(50), SetResult.Distance(1300.25))
        val id = open { database ->
            val exercises = listOf(bench, pushup, plank, CatalogExercise("run", "Run", ExerciseType.DISTANCE))
            val day = saveDays(database, exercises.map { trainingExercise(it) }).single()
            val repository = RoomWorkoutsRepository(database)
            val workoutId = requireNotNull(repository.startWorkout(day.id))
            for ((exercise, result) in repository.workout(workoutId).exercises.zip(initial)) {
                assertTrue(repository.logSet(workoutId, exercise, LoggedSet(1, result)))
                if (result is SetResult.Duration) {
                    assertTrue(repository.updateInProgressSet(workoutId, exercise.id, LoggedSet(1, SetResult.Duration(49))))
                }
            }
            workoutId
        }

        open { database ->
            val repository = RoomWorkoutsRepository(database)
            assertEquals(id, repository.inProgressWorkout.first()?.id)
            val sets = repository.workout(id).exercises.map { it.sets.single() }
            assertEquals(
                listOf(SetResult.WeightAndReps(62.5, 8), SetResult.Reps(5), SetResult.Duration(49), SetResult.Distance(1250.5)),
                sets.map { it.result },
            )
            assertTrue(repository.finishWorkout(id))
            assertFalse(repository.updateCompletedSet(id, sets[2].id, SetResult.Reps(50)))
            for ((set, result) in sets.zip(edited)) {
                assertTrue(repository.updateCompletedSet(id, set.id, result))
            }
        }

        open { database ->
            val repository = RoomWorkoutsRepository(database)
            val workout = repository.workout(id)
            assertNotNull(workout.summary.finishedAtMillis)
            val sets = workout.exercises.map { it.sets.single() }
            assertEquals(edited, sets.map { it.result })
            for (set in sets.dropLast(1)) {
                assertTrue(repository.deleteCompletedSet(id, set.id))
            }
            assertFalse(repository.deleteCompletedSet(id, sets.last().id))
            assertEquals(listOf(sets.last()), repository.workout(id).exercises.flatMap { it.sets })
            assertTrue(repository.deleteCompletedWorkout(id))
            assertEquals(emptyList<WorkoutSummary>(), repository.history.first())
        }
    }

    @Test
    fun replacementsReopenInPlaceWithGoalsForTheirTypeAndDoNotChangeTheTrainingDay() = withWorkoutDatabase {
        val dumbbellBench = CatalogExercise("dumbbell-bench", "Dumbbell bench", ExerciseType.WEIGHT_AND_REPS)
        val goal = ExerciseGoal.Repetitions(8)
        val (dayId, workoutId) = open { database ->
            val day = saveDays(
                database,
                listOf(trainingExercise(bench, goal), trainingExercise(bench, goal), trainingExercise(plank)),
            ).single()
            val repository = RoomWorkoutsRepository(database)
            val id = requireNotNull(repository.startWorkout(day.id))
            val (first, second) = repository.workout(id).exercises
            assertNotNull(repository.removeSetPosition(id, second, position = 1, isRecorded = false))
            assertTrue(repository.replaceExercise(id, first.id, bench.id, dumbbellBench))
            assertTrue(repository.replaceExercise(id, second.id, bench.id, pushup))
            day.id to id
        }

        open { database ->
            val repository = RoomWorkoutsRepository(database)
            val exercises = repository.workout(workoutId).exercises
            assertEquals(listOf(dumbbellBench.id, pushup.id, plank.id), exercises.map { it.catalogExerciseId })
            assertEquals(listOf(goal, null, null), exercises.map { it.goal })
            assertEquals(listOf(3, 3, 3), exercises.map { it.recommendedSets })
            assertEquals(ExerciseType.REPS, exercises[1].type)
            assertEquals(emptySet<Int>(), exercises[1].removedPositions)

            assertTrue(repository.logSet(workoutId, exercises[0], LoggedSet(1, SetResult.WeightAndReps(22.5, 8))))
            assertTrue(repository.finishWorkout(workoutId))
            val history = repository.workout(workoutId)
            assertEquals(dumbbellBench.name, history.exercises.first().name)
            assertEquals(goal, history.exercises.first().goal)
            assertEquals(SetResult.WeightAndReps(22.5, 8), history.exercises.first().sets.single().result)

            val freshId = requireNotNull(repository.startWorkout(dayId))
            val fresh = repository.workout(freshId).exercises
            assertEquals(listOf(bench.id, bench.id, plank.id), fresh.map { it.catalogExerciseId })
            assertEquals(listOf(goal, goal, null), fresh.map { it.goal })
        }
    }

    @Test
    fun deletingARecordedSetDoesNotUnlockReplacementAfterReopening() = withWorkoutDatabase {
        val workoutId = open { database ->
            val repository = RoomWorkoutsRepository(database)
            val day = saveDays(database, listOf(trainingExercise(bench))).single()
            val id = requireNotNull(repository.startWorkout(day.id))
            val original = repository.workout(id).exercises.single()
            assertTrue(repository.replaceExercise(id, original.id, bench.id, pushup))
            val replacement = repository.workout(id).exercises.single()
            assertTrue(repository.logSet(id, replacement, LoggedSet(1, SetResult.Reps(6))))
            val removed = requireNotNull(repository.removeSetPosition(id, replacement, position = 1, isRecorded = true))
            assertEquals(SetResult.Reps(6), removed.set?.result)
            assertTrue(repository.restoreSetPosition(id, replacement.id, 1, removed.set))
            assertFalse(repository.replaceExercise(id, replacement.id, pushup.id, bench))
            assertNotNull(repository.removeSetPosition(id, replacement, position = 1, isRecorded = true))
            id
        }

        open { database ->
            val repository = RoomWorkoutsRepository(database)
            val replacement = repository.workout(workoutId).exercises.single()
            assertEquals(pushup.id, replacement.catalogExerciseId)
            assertTrue(replacement.sets.isEmpty())
            assertFalse(repository.replaceExercise(workoutId, replacement.id, pushup.id, bench))
        }
    }

    private suspend fun RoomWorkoutsRepository.workout(id: Long) = requireNotNull(observeWorkout(id).first())

    private fun FittyDatabase.sessionTimes(id: Long): Pair<Long, Long> =
        openHelper.readableDatabase.query(
            "SELECT startedAtMillis, finishedAtMillis FROM workout_sessions WHERE id = ?",
            arrayOf(id.toString()),
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0) to cursor.getLong(1)
        }

    private suspend fun saveDays(database: FittyDatabase, vararg exercises: List<TrainingExercise>): List<TrainingDay> {
        val programs = RoomProgramsRepository(database)
        val days = exercises.mapIndexed { index, day -> TrainingDay(0, "Day ${index + 1}", day) }
        programs.saveProgram(TrainingProgram(0, "Strength", days))
        return requireNotNull(programs.homeProgram.first()).days
    }

    private fun trainingExercise(exercise: CatalogExercise, goal: ExerciseGoal? = null) =
        TrainingExercise(0, exercise.name, 3, goal, exercise.id, exercise.type)

    private fun loggedSet() = LoggedSet(1, SetResult.WeightAndReps(60.0, 8))

    private fun withWorkoutDatabase(test: suspend WorkoutDatabase.() -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "workout-results-test.db"
        context.deleteDatabase(name)
        try {
            runBlocking { withTimeout(10_000) { WorkoutDatabase(context, name).test() } }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private class WorkoutDatabase(private val context: Context, private val name: String) {
        suspend fun <T> open(test: suspend (FittyDatabase) -> T): T {
            val database = Room.databaseBuilder(context, FittyDatabase::class.java, name).build()
            try {
                return test(database)
            } finally {
                database.close()
            }
        }
    }

    private val bench = CatalogExercise("bench", "Bench press", ExerciseType.WEIGHT_AND_REPS)
    private val pushup = CatalogExercise("pushup", "Push-up", ExerciseType.REPS)
    private val plank = CatalogExercise("plank", "Plank", ExerciseType.DURATION)
}
