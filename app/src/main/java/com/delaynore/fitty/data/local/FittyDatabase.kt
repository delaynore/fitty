package com.delaynore.fitty.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

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
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
internal abstract class FittyDatabase : RoomDatabase() {
    abstract fun programs(): ProgramDao
    abstract fun workouts(): WorkoutDao
}
