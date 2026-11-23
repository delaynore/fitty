package com.delaynore.fitty.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrainingProgramEntity::class,
        TrainingDayEntity::class,
        TrainingExerciseEntity::class,
        HomeProgramEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        LoggedSetEntity::class,
        RemovedSetPositionEntity::class,
        CustomExerciseEntity::class,
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = PreserveLegacySetTypes::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = PreserveWorkoutReplacementLocks::class),
    ],
)
internal abstract class FittyDatabase : RoomDatabase() {
    abstract fun programs(): ProgramDao
    abstract fun workouts(): WorkoutDao
    abstract fun customExercises(): CustomExerciseDao
}

internal class PreserveLegacySetTypes : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // Version 5 offered only weight and repetitions, even for other catalog types.
        db.execSQL(
            """
            UPDATE workout_exercises SET type = 'WEIGHT_AND_REPS'
            WHERE type != 'WEIGHT_AND_REPS'
              AND EXISTS(SELECT 1 FROM logged_sets WHERE exerciseId = workout_exercises.id)
            """.trimIndent(),
        )
    }
}

internal class PreserveWorkoutReplacementLocks : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE workout_exercises SET hasEverLoggedSet = 1
            WHERE EXISTS(SELECT 1 FROM logged_sets WHERE exerciseId = workout_exercises.id)
            """.trimIndent(),
        )
    }
}
