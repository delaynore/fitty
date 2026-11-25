package com.delaynore.fitty.data.exercises

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.RoomWorkoutsRepository
import com.delaynore.fitty.domain.catalogExerciseComparator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomExerciseCatalogRepositoryTest {
    @Test
    fun customExerciseIsMergedIntoTheCanonicalCatalogOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "catalog-order-test.db"
        context.deleteDatabase(databaseName)
        try {
            runBlocking {
                withTimeout(10_000) {
                    withDatabase(context, databaseName) { database ->
                        val catalog = RoomExerciseCatalogRepository(database)
                        val custom = catalog.create("мертвый жук", ExerciseType.REPS)
                        val exercises = catalog.allExercises.first()
                        val builtInIndex = exercises.indexOfFirst { it.id == "dead-bug" }

                        assertEquals(exercises.sortedWith(catalogExerciseComparator), exercises)
                        assertEquals(builtInIndex + 1, exercises.indexOf(custom))
                    }
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun editedCustomExerciseReopensInTheCatalogAndCanBeUsedInAnotherDay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "catalog-persistence-test.db"
        context.deleteDatabase(databaseName)
        try {
            runBlocking {
                withTimeout(10_000) {
                    val created = withDatabase(context, databaseName) { database ->
                        val catalog = RoomExerciseCatalogRepository(database)
                        val exercise = catalog.create("Боковое удержание", ExerciseType.DURATION)
                        val shortCard = catalog.allExercises.first().single { it.name == "Боковое удержание" }
                        assertNull(shortCard.difficulty)
                        assertNull(shortCard.technique)
                        RoomProgramsRepository(database).saveProgram(TrainingProgram(
                            id = 0,
                            name = "Силовая",
                            days = listOf(TrainingDay(0, "День А", listOf(trainingExercise(shortCard)))),
                        ))
                        catalog.saveDetails(exercise.copy(
                            difficulty = TechniqueDifficulty.INTERMEDIATE,
                            technique = "Удерживай корпус на одной линии.",
                        ))
                        exercise
                    }
                    withDatabase(context, databaseName) { database ->
                        val reopened = RoomExerciseCatalogRepository(database)
                            .allExercises.first().single { it.name == "Боковое удержание" }
                        assertEquals(created.id, reopened.id)
                        assertEquals(ExerciseType.DURATION, reopened.type)
                        assertEquals(TechniqueDifficulty.INTERMEDIATE, reopened.difficulty)
                        assertEquals("Удерживай корпус на одной линии.", reopened.technique)

                        val programs = RoomProgramsRepository(database)
                        val program = requireNotNull(programs.homeProgram.first())
                        val secondDay = TrainingDay(0, "День Б", listOf(trainingExercise(reopened)))
                        programs.saveProgram(program.copy(days = program.days + secondDay))
                        val saved = requireNotNull(programs.observeProgram(program.id).first())
                        assertEquals(listOf("День А", "День Б"), saved.days.map { it.name })
                        assertEquals(listOf(created.id, created.id), saved.days.map { it.exercises.single().catalogExerciseId })

                        val workouts = RoomWorkoutsRepository(database)
                        val workoutId = requireNotNull(workouts.startWorkout(saved.days.last().id))
                        val workout = requireNotNull(workouts.observeWorkout(workoutId).first())
                        assertEquals("День Б", workout.summary.dayName)
                        assertEquals(ExerciseType.DURATION, workout.exercises.single().type)
                        assertEquals(created.id, workout.exercises.single().catalogExerciseId)
                    }
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private inline fun <T> withDatabase(context: Context, name: String, test: (FittyDatabase) -> T): T {
        val database = Room.databaseBuilder(context, FittyDatabase::class.java, name).build()
        try {
            return test(database)
        } finally {
            database.close()
        }
    }

    private fun trainingExercise(exercise: CatalogExercise) = TrainingExercise(
        id = 0,
        name = exercise.name,
        recommendedSets = null,
        goal = null,
        catalogExerciseId = exercise.id,
        type = exercise.type,
    )
}
