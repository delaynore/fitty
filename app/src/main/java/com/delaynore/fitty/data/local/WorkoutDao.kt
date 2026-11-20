package com.delaynore.fitty.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workout_sessions")
internal data class WorkoutSessionEntity(
    val programName: String,
    val dayName: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sessionId", "position"], unique = true)],
)
internal data class WorkoutExerciseEntity(
    val sessionId: Long,
    val position: Int,
    val name: String,
    val recommendedSets: Int? = null,
    val recommendedRepetitions: Int? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "logged_sets",
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["exerciseId", "position"], unique = true)],
)
internal data class LoggedSetEntity(
    val exerciseId: Long,
    val position: Int,
    val weightKg: Double,
    val repetitions: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "removed_set_positions",
    primaryKeys = ["exerciseId", "position"],
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class RemovedSetPositionEntity(val exerciseId: Long, val position: Int)

internal data class StoredWorkoutExercise(
    @Embedded val exercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<LoggedSetEntity>,
    @Relation(parentColumn = "id", entityColumn = "exerciseId", entity = RemovedSetPositionEntity::class, projection = ["position"])
    val removedPositions: List<Int>,
)

internal data class StoredWorkout(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<StoredWorkoutExercise>,
)

internal data class StoredInProgressWorkout(val id: Long, val dayName: String, val setCount: Int)

@Dao
internal interface WorkoutDao {
    @Query("""
        SELECT workout_sessions.id, dayName, (
            SELECT COUNT(*) FROM logged_sets
            JOIN workout_exercises ON logged_sets.exerciseId = workout_exercises.id
            WHERE workout_exercises.sessionId = workout_sessions.id
        ) AS setCount
        FROM workout_sessions WHERE finishedAtMillis IS NULL LIMIT 1
    """)
    fun observeInProgressWorkout(): Flow<StoredInProgressWorkout?>

    @Query("SELECT id FROM workout_sessions WHERE finishedAtMillis IS NULL LIMIT 1")
    suspend fun inProgressWorkoutId(): Long?

    @Query("SELECT * FROM workout_sessions WHERE finishedAtMillis IS NOT NULL ORDER BY finishedAtMillis DESC, id DESC")
    fun observeHistory(): Flow<List<WorkoutSessionEntity>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeWorkout(id: Long): Flow<StoredWorkout?>

    @Insert suspend fun insertSession(session: WorkoutSessionEntity): Long
    @Insert suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>)
    @Insert suspend fun insertSet(set: LoggedSetEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemovedSetPosition(position: RemovedSetPositionEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM removed_set_positions WHERE exerciseId = :exerciseId AND position = :position)")
    suspend fun isRemovedSetPosition(exerciseId: Long, position: Int): Boolean

    @Query("DELETE FROM removed_set_positions WHERE exerciseId = :exerciseId AND position = :position")
    suspend fun restoreSetPosition(exerciseId: Long, position: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM workout_exercises WHERE id = :exerciseId AND sessionId = :sessionId)")
    suspend fun exerciseBelongsToWorkout(exerciseId: Long, sessionId: Long): Boolean

    @Query("SELECT * FROM logged_sets WHERE exerciseId = :exerciseId AND position = :position")
    suspend fun loggedSetAtPosition(exerciseId: Long, position: Int): LoggedSetEntity?

    @Query("UPDATE logged_sets SET weightKg = :weightKg, repetitions = :repetitions WHERE exerciseId = :exerciseId AND position = :position")
    suspend fun updateInProgressSet(exerciseId: Long, position: Int, weightKg: Double, repetitions: Int): Int

    @Query("DELETE FROM logged_sets WHERE id = :id")
    suspend fun deleteInProgressSet(id: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM logged_sets JOIN workout_exercises ON logged_sets.exerciseId = workout_exercises.id WHERE workout_exercises.sessionId = :sessionId)")
    suspend fun hasLoggedSets(sessionId: Long): Boolean

    @Query("UPDATE workout_sessions SET finishedAtMillis = :finishedAtMillis WHERE id = :sessionId AND finishedAtMillis IS NULL")
    suspend fun finishWorkout(sessionId: Long, finishedAtMillis: Long): Int

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId AND finishedAtMillis IS NULL")
    suspend fun cancelWorkout(sessionId: Long): Int

    @Query(
        """
        UPDATE logged_sets SET weightKg = :weightKg, repetitions = :repetitions
        WHERE id = :setId AND exerciseId IN (
            SELECT workout_exercises.id FROM workout_exercises
            JOIN workout_sessions ON workout_sessions.id = workout_exercises.sessionId
            WHERE workout_sessions.id = :sessionId AND workout_sessions.finishedAtMillis IS NOT NULL
        )
        """,
    )
    suspend fun updateCompletedSet(sessionId: Long, setId: Long, weightKg: Double, repetitions: Int): Int

    @Query(
        """
        DELETE FROM logged_sets
        WHERE id = :setId AND exerciseId IN (
            SELECT workout_exercises.id FROM workout_exercises
            JOIN workout_sessions ON workout_sessions.id = workout_exercises.sessionId
            WHERE workout_sessions.id = :sessionId AND workout_sessions.finishedAtMillis IS NOT NULL
        ) AND (
            SELECT COUNT(*) FROM logged_sets
            JOIN workout_exercises ON workout_exercises.id = logged_sets.exerciseId
            WHERE workout_exercises.sessionId = :sessionId
        ) > 1
        """,
    )
    suspend fun deleteCompletedSet(sessionId: Long, setId: Long): Int

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId AND finishedAtMillis IS NOT NULL")
    suspend fun deleteCompletedWorkout(sessionId: Long): Int
}
