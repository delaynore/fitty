package com.delaynore.fitty.data.programs

import androidx.room.withTransaction
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.local.HomeProgramEntity
import com.delaynore.fitty.data.local.TrainingDayEntity
import com.delaynore.fitty.data.local.TrainingExerciseEntity
import com.delaynore.fitty.data.local.TrainingProgramEntity
import kotlinx.coroutines.flow.map

internal data class TrainingDay(val id: Long, val name: String, val exercises: List<String>)

internal data class TrainingProgram(val name: String, val days: List<TrainingDay>)

internal class ProgramsRepository(private val database: FittyDatabase) {
    private val dao = database.programs()

    val homeProgram = dao.observeHomeProgram().map { stored ->
        stored?.let {
            TrainingProgram(
                name = it.program.name,
                days = it.days.sortedBy { day -> day.day.id }.map { day ->
                    TrainingDay(day.day.id, day.day.name, day.exercises.sortedBy { exercise -> exercise.id }.map { it.name })
                },
            )
        }
    }

    suspend fun createProgram(name: String, dayName: String, exerciseName: String) {
        require(name.isNotBlank() && dayName.isNotBlank() && exerciseName.isNotBlank())
        database.withTransaction {
            val programId = dao.insertProgram(TrainingProgramEntity(name.trim()))
            val dayId = dao.insertDay(TrainingDayEntity(programId, dayName.trim()))
            dao.insertExercise(TrainingExerciseEntity(dayId, exerciseName.trim()))
            if (dao.homeProgramId() == null) dao.selectHomeProgram(HomeProgramEntity(programId))
        }
    }
}
