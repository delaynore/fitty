package com.delaynore.fitty.data.local

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
    ],
    version = 1,
)
internal abstract class FittyDatabase : RoomDatabase() {
    abstract fun programs(): ProgramDao
    abstract fun workouts(): WorkoutDao
}
