package com.delaynore.fitty.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_exercises")
internal data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val equipment: String,
    val primaryMuscle: String?,
    val assistingMuscles: String,
    val difficulty: String?,
    val technique: String?,
)

@Dao
internal interface CustomExerciseDao {
    @Query("SELECT * FROM custom_exercises")
    fun observeAll(): Flow<List<CustomExerciseEntity>>

    @Insert
    suspend fun insert(exercise: CustomExerciseEntity)

    @Update
    suspend fun update(exercise: CustomExerciseEntity)
}
