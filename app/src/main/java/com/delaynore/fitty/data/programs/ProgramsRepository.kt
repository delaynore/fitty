package com.delaynore.fitty.data.programs

import androidx.room.withTransaction
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.HomeProgramEntity
import com.delaynore.fitty.data.local.StoredTrainingProgram
import com.delaynore.fitty.data.local.TrainingDayEntity
import com.delaynore.fitty.data.local.TrainingExerciseEntity
import com.delaynore.fitty.data.local.TrainingProgramEntity
import kotlinx.coroutines.flow.map

internal data class TrainingExercise(
    val id: Long,
    val name: String,
    val recommendedSets: Int?,
    val recommendedRepetitions: Int?,
)

internal data class TrainingDay(val id: Long, val name: String, val exercises: List<TrainingExercise>)

internal data class TrainingProgram(val id: Long, val name: String, val days: List<TrainingDay>)

internal data class ProgramSelection(val id: Long, val name: String, val selected: Boolean)

internal class ProgramsRepository(private val database: FittyDatabase) {
    private val dao = database.programs()

    val homeProgram = dao.observeHomeProgram().map { it?.toProgram() }
    val allPrograms = dao.observePrograms().map { programs ->
        programs.map { ProgramSelection(it.program.id, it.program.name, it.selected) }
    }

    fun observeProgram(id: Long) = dao.observeProgram(id).map { it?.toProgram() }

    suspend fun selectProgram(id: Long) {
        dao.selectHomeProgram(HomeProgramEntity(id))
    }

    suspend fun saveProgram(program: TrainingProgram) {
        require(program.name.isNotBlank() && program.days.isNotEmpty())
        require(program.days.all { day ->
            day.name.isNotBlank() && day.exercises.isNotEmpty() && day.exercises.all {
                it.name.isNotBlank() && (it.recommendedSets == null || it.recommendedSets > 0) &&
                    (it.recommendedRepetitions == null || it.recommendedRepetitions > 0)
            }
        })

        database.withTransaction {
            val stored = if (program.id > 0) checkNotNull(dao.trainingProgram(program.id)) else null
            val entity = TrainingProgramEntity(program.name.trim(), program.id.coerceAtLeast(0))
            val id = if (stored == null) dao.insertProgram(entity) else {
                dao.updateProgram(entity)
                entity.id
            }

            val priorDays = stored?.days.orEmpty().associateBy { it.day.id }
            dao.deleteDays((priorDays.keys - program.days.map { it.id }.toSet()).toList())

            for (day in program.days) {
                val prior = priorDays[day.id]
                val dayEntity = TrainingDayEntity(id, day.name.trim(), prior?.day?.id ?: 0)
                val dayId = if (prior == null) dao.insertDay(dayEntity) else {
                    dao.updateDay(dayEntity)
                    dayEntity.id
                }
                val priorExerciseIds = prior?.exercises.orEmpty().map { it.id }.toSet()
                dao.deleteExercises((priorExerciseIds - day.exercises.map { it.id }.toSet()).toList())
                for (exercise in day.exercises) {
                    val exerciseEntity = TrainingExerciseEntity(
                        dayId = dayId,
                        name = exercise.name.trim(),
                        recommendedSets = exercise.recommendedSets,
                        recommendedRepetitions = exercise.recommendedRepetitions,
                        id = exercise.id.takeIf { it in priorExerciseIds } ?: 0,
                    )
                    if (exerciseEntity.id == 0L) dao.insertExercise(exerciseEntity)
                    else dao.updateExercise(exerciseEntity)
                }
            }
            if (dao.homeProgramId() == null) dao.selectHomeProgram(HomeProgramEntity(id))
        }
    }

    private fun StoredTrainingProgram.toProgram() = TrainingProgram(
        id = program.id,
        name = program.name,
        days = days.sortedBy { it.day.id }.map { stored ->
            TrainingDay(stored.day.id, stored.day.name, stored.exercises.sortedBy { it.id }.map {
                TrainingExercise(it.id, it.name, it.recommendedSets, it.recommendedRepetitions)
            })
        },
    )
}
