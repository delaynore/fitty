package com.delaynore.fitty.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.delaynore.fitty.MigrationTest
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.RoomExerciseCatalogRepository
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.workouts.RoomWorkoutsRepository
import com.delaynore.fitty.data.workouts.SetResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FittyDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FittyDatabase::class.java,
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    @MigrationTest
    fun migratesEveryVersionFromOneToEightAndPreservesWorkoutData() = runBlocking {
        helper.createDatabase(databaseName, 1).let { database ->
            try { insertVersionOneRecords(database) } finally { database.close() }
        }

        for (targetVersion in 2..8) {
            helper.runMigrationsAndValidate(databaseName, targetVersion, true, *migrations)
        }

        withOpenDatabase { database ->
            val programs = RoomProgramsRepository(database)
            val selected = requireNotNull(programs.homeProgram.first())
            assertEquals(1L, selected.id)
            assertEquals("Strength", selected.name)
            assertEquals(listOf("Day A"), selected.days.map { it.name })
            assertEquals(listOf("Bench", "Plank"), selected.days.single().exercises.map { it.name })
            assertEquals(listOf(null, null), selected.days.single().exercises.map { it.recommendedSets })
            assertEquals(listOf(null, null), selected.days.single().exercises.map { it.goal })
            assertEquals(listOf(null, null), selected.days.single().exercises.map { it.catalogExerciseId })
            assertEquals(
                listOf(ExerciseType.WEIGHT_AND_REPS, ExerciseType.WEIGHT_AND_REPS),
                selected.days.single().exercises.map { it.type },
            )

            val workouts = RoomWorkoutsRepository(database)
            assertEquals(listOf(200L), workouts.history.first().map { it.id })
            assertEquals(201L, workouts.inProgressWorkout.first()?.id)
            val completed = requireNotNull(workouts.observeWorkout(200).first())
            assertEquals(SetResult.WeightAndReps(80.0, 5), completed.exercises.single().sets.single().result)
            assertEquals(ExerciseType.WEIGHT_AND_REPS, completed.exercises.single().type)
            assertTrue(completed.exercises.single().hasEverLoggedSet)
            val active = requireNotNull(workouts.observeWorkout(201).first())
            assertEquals(2, active.exercises.size)
            assertEquals(SetResult.WeightAndReps(60.0, 8), active.exercises.first().sets.single().result)
            assertTrue(active.exercises.last().sets.isEmpty())
            assertEquals(listOf(ExerciseType.WEIGHT_AND_REPS, ExerciseType.WEIGHT_AND_REPS), active.exercises.map { it.type })
            assertEquals(listOf(true, false), active.exercises.map { it.hasEverLoggedSet })
            assertEquals(1, database.rowCount("training_programs"))
            assertEquals(1, database.rowCount("training_days"))
            assertEquals(2, database.rowCount("training_exercises"))
            assertEquals(1, database.rowCount("home_program"))
            assertEquals(2, database.rowCount("workout_sessions"))
            assertEquals(3, database.rowCount("workout_exercises"))
            assertEquals(2, database.rowCount("logged_sets"))
        }
    }

    @Test
    @MigrationTest
    fun migrationFiveToSixPreservesLegacySetTypes() = runBlocking {
        helper.createDatabase(databaseName, 5).let { database ->
            try {
                insertSession(database, id = 1)
                insertVersionFiveExercise(database, id = 10, position = 0, name = "Plank", type = "DURATION")
                insertVersionFiveExercise(database, id = 11, position = 1, name = "Run", type = "DISTANCE")
                database.execSQL("INSERT INTO logged_sets (exerciseId, position, weightKg, repetitions, id) VALUES (10, 1, 0, 45, 100)")
            } finally { database.close() }
        }

        helper.runMigrationsAndValidate(databaseName, 6, true, *migrations)

        withOpenDatabase { database ->
            val workout = requireNotNull(RoomWorkoutsRepository(database).observeWorkout(1).first())
            assertEquals(ExerciseType.WEIGHT_AND_REPS, workout.exercises[0].type)
            assertEquals(SetResult.WeightAndReps(0.0, 45), workout.exercises[0].sets.single().result)
            assertEquals(ExerciseType.DISTANCE, workout.exercises[1].type)
            assertTrue(workout.exercises[1].sets.isEmpty())
        }
    }

    @Test
    @MigrationTest
    fun migrationSevenToEightPreservesWorkoutReplacementLocks() = runBlocking {
        helper.createDatabase(databaseName, 7).let { database ->
            try {
                insertSession(database, id = 1)
                insertVersionSevenExercise(database, id = 10, position = 0, name = "Bench", catalogId = "bench")
                insertVersionSevenExercise(database, id = 11, position = 1, name = "Press", catalogId = "press")
                database.execSQL("INSERT INTO logged_sets (exerciseId, position, weightKg, repetitions, id) VALUES (10, 1, 50, 8, 100)")
                database.execSQL(
                    "INSERT INTO custom_exercises (id, name, type, equipment, primaryMuscle, assistingMuscles, difficulty, technique) " +
                        "VALUES ('custom:mypress', 'My press', 'WEIGHT_AND_REPS', '', NULL, '', NULL, NULL)",
                )
            } finally { database.close() }
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, *migrations)

        withOpenDatabase { database ->
            val workouts = RoomWorkoutsRepository(database)
            val workout = requireNotNull(workouts.observeWorkout(1).first())
            assertTrue(workout.exercises[0].hasEverLoggedSet)
            assertFalse(workout.exercises[1].hasEverLoggedSet)
            val replacement = CatalogExercise("replacement", "Replacement", ExerciseType.WEIGHT_AND_REPS)
            assertFalse(workouts.replaceExercise(1, 10, "bench", replacement))
            assertTrue(workouts.replaceExercise(1, 11, "press", replacement))
            assertNotNull(RoomExerciseCatalogRepository(database).allExercises.first().find { it.id == "custom:mypress" })
        }
    }

    private val migrations: Array<Migration>
        get() = FittyDatabase_Impl().createAutoMigrations(
            mapOf(
                PreserveLegacySetTypes::class to PreserveLegacySetTypes(),
                PreserveWorkoutReplacementLocks::class to PreserveWorkoutReplacementLocks(),
            ),
        ).toTypedArray()

    private fun openDatabase(): FittyDatabase = Room.databaseBuilder(context, FittyDatabase::class.java, databaseName).build()

    private fun FittyDatabase.rowCount(table: String): Int =
        openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private suspend fun <T> withOpenDatabase(block: suspend (FittyDatabase) -> T): T {
        val database = openDatabase()
        return try { block(database) } finally { database.close() }
    }

    private fun insertVersionOneRecords(database: SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO training_programs (name, id) VALUES ('Strength', 1)")
        database.execSQL("INSERT INTO training_days (programId, name, id) VALUES (1, 'Day A', 10)")
        database.execSQL("INSERT INTO training_exercises (dayId, name, id) VALUES (10, 'Bench', 100)")
        database.execSQL("INSERT INTO training_exercises (dayId, name, id) VALUES (10, 'Plank', 101)")
        database.execSQL("INSERT INTO home_program (programId, id) VALUES (1, 0)")
        insertSession(database, id = 200, finishedAtMillis = 2_000)
        insertSession(database, id = 201)
        database.execSQL("INSERT INTO workout_exercises (sessionId, position, name, id) VALUES (200, 0, 'Bench', 300)")
        database.execSQL("INSERT INTO workout_exercises (sessionId, position, name, id) VALUES (201, 0, 'Bench', 301)")
        database.execSQL("INSERT INTO workout_exercises (sessionId, position, name, id) VALUES (201, 1, 'Plank', 302)")
        database.execSQL("INSERT INTO logged_sets (exerciseId, position, weightKg, repetitions, id) VALUES (300, 1, 80, 5, 400)")
        database.execSQL("INSERT INTO logged_sets (exerciseId, position, weightKg, repetitions, id) VALUES (301, 1, 60, 8, 401)")
    }

    private fun insertSession(database: SupportSQLiteDatabase, id: Long, finishedAtMillis: Long? = null) {
        database.execSQL(
            "INSERT INTO workout_sessions (programName, dayName, startedAtMillis, finishedAtMillis, id) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any?>("Strength", "Day A", 1_000L, finishedAtMillis, id),
        )
    }

    private fun insertVersionFiveExercise(
        database: SupportSQLiteDatabase,
        id: Long,
        position: Int,
        name: String,
        type: String,
    ) {
        database.execSQL(
            "INSERT INTO workout_exercises (sessionId, position, name, recommendedSets, recommendedRepetitions, catalogExerciseId, type, id) " +
                "VALUES (1, ?, ?, NULL, NULL, ?, ?, ?)",
            arrayOf<Any>(position, name, name.lowercase(), type, id),
        )
    }

    private fun insertVersionSevenExercise(
        database: SupportSQLiteDatabase,
        id: Long,
        position: Int,
        name: String,
        catalogId: String,
    ) {
        database.execSQL(
            "INSERT INTO workout_exercises (sessionId, position, name, recommendedSets, recommendedRepetitions, catalogExerciseId, type, " +
                "recommendedDurationSeconds, recommendedDistanceMeters, id) VALUES (1, ?, ?, NULL, NULL, ?, 'WEIGHT_AND_REPS', NULL, NULL, ?)",
            arrayOf<Any>(position, name, catalogId, id),
        )
    }
}
