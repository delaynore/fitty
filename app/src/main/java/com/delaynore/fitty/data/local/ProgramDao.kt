package com.delaynore.fitty.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_programs")
internal data class TrainingProgramEntity(
    val name: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "training_days",
    foreignKeys = [ForeignKey(
        entity = TrainingProgramEntity::class,
        parentColumns = ["id"],
        childColumns = ["programId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("programId")],
)
internal data class TrainingDayEntity(
    val programId: Long,
    val name: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "training_exercises",
    foreignKeys = [ForeignKey(
        entity = TrainingDayEntity::class,
        parentColumns = ["id"],
        childColumns = ["dayId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("dayId")],
)
internal data class TrainingExerciseEntity(
    val dayId: Long,
    val name: String,
    val recommendedSets: Int? = null,
    val recommendedRepetitions: Int? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(
    tableName = "home_program",
    foreignKeys = [ForeignKey(
        entity = TrainingProgramEntity::class,
        parentColumns = ["id"],
        childColumns = ["programId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("programId")],
)
internal data class HomeProgramEntity(
    val programId: Long,
    @PrimaryKey val id: Int = 0,
)

internal data class StoredTrainingDay(
    @Embedded val day: TrainingDayEntity,
    @Relation(parentColumn = "id", entityColumn = "dayId")
    val exercises: List<TrainingExerciseEntity>,
)

// Migrated exercises all have position 0. Their IDs preserve the original order.
internal fun StoredTrainingDay.orderedExercises() =
    exercises.sortedWith(compareBy(TrainingExerciseEntity::position, TrainingExerciseEntity::id))

internal data class StoredTrainingProgram(
    @Embedded val program: TrainingProgramEntity,
    @Relation(entity = TrainingDayEntity::class, parentColumn = "id", entityColumn = "programId")
    val days: List<StoredTrainingDay>,
)

internal data class StoredProgramSelection(
    @Embedded val program: TrainingProgramEntity,
    val selected: Boolean,
)

@Dao
internal interface ProgramDao {
    @Transaction
    @Query("SELECT training_programs.* FROM training_programs JOIN home_program ON home_program.programId = training_programs.id WHERE home_program.id = 0")
    fun observeHomeProgram(): Flow<StoredTrainingProgram?>

    @Query("SELECT *, id = COALESCE((SELECT programId FROM home_program WHERE id = 0), 0) AS selected FROM training_programs ORDER BY id")
    fun observePrograms(): Flow<List<StoredProgramSelection>>

    @Transaction
    @Query("SELECT * FROM training_programs WHERE id = :programId")
    fun observeProgram(programId: Long): Flow<StoredTrainingProgram?>

    @Transaction
    @Query("SELECT * FROM training_programs WHERE id = :programId")
    suspend fun trainingProgram(programId: Long): StoredTrainingProgram?

    @Query("SELECT programId FROM home_program WHERE id = 0")
    suspend fun homeProgramId(): Long?

    @Transaction
    @Query("SELECT * FROM training_days WHERE id = :dayId")
    suspend fun trainingDay(dayId: Long): StoredTrainingDay?

    @Query("SELECT name FROM training_programs WHERE id = :programId")
    suspend fun programName(programId: Long): String

    @Insert suspend fun insertProgram(program: TrainingProgramEntity): Long
    @Insert suspend fun insertDay(day: TrainingDayEntity): Long
    @Insert suspend fun insertExercise(exercise: TrainingExerciseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun selectHomeProgram(selection: HomeProgramEntity)

    @Update suspend fun updateProgram(program: TrainingProgramEntity)
    @Update suspend fun updateDay(day: TrainingDayEntity)
    @Update suspend fun updateExercise(exercise: TrainingExerciseEntity)

    @Query("DELETE FROM training_days WHERE id IN (:ids)")
    suspend fun deleteDays(ids: List<Long>)

    @Query("DELETE FROM training_exercises WHERE id IN (:ids)")
    suspend fun deleteExercises(ids: List<Long>)
}
