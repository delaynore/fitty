package com.delaynore.fitty.data.programs

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import kotlinx.coroutines.flow.Flow

internal data class TrainingExercise(
    val id: Long,
    val name: String,
    val recommendedSets: Int?,
    val goal: ExerciseGoal?,
    val catalogExerciseId: String? = null,
    val type: ExerciseType = ExerciseType.WEIGHT_AND_REPS,
)

internal data class TrainingDay(val id: Long, val name: String, val exercises: List<TrainingExercise>)

internal data class TrainingProgram(val id: Long, val name: String, val days: List<TrainingDay>)

internal data class ProgramSelection(val id: Long, val name: String, val selected: Boolean)

internal interface ProgramsRepository {
    val homeProgram: Flow<TrainingProgram?>
    val allPrograms: Flow<List<ProgramSelection>>
    fun observeProgram(id: Long): Flow<TrainingProgram?>
    suspend fun selectProgram(id: Long)
    suspend fun saveProgram(program: TrainingProgram)
}
