package com.delaynore.fitty.data.exercises

import com.delaynore.fitty.data.local.CustomExerciseEntity
import com.delaynore.fitty.data.local.FittyDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ExerciseCatalogRepository(database: FittyDatabase) {
    private val custom = database.customExercises()

    val allExercises: Flow<List<CatalogExercise>> = custom.observeAll().map { exercises ->
        BuiltInExercises.all + exercises.map { it.toExercise() }
    }

    suspend fun create(name: String, type: ExerciseType): CatalogExercise {
        require(name.isNotBlank())
        val exercise = CatalogExercise("$CUSTOM_EXERCISE_ID_PREFIX${UUID.randomUUID()}", name.trim(), type)
        custom.insert(exercise.toEntity())
        return exercise
    }

    suspend fun saveDetails(exercise: CatalogExercise) {
        require(exercise.isCustom)
        custom.update(exercise.copy(technique = exercise.technique?.trim()?.takeIf(String::isNotEmpty)).toEntity())
    }
}

private fun CustomExerciseEntity.toExercise() = CatalogExercise(
    id = id,
    name = name,
    type = ExerciseType.valueOf(type),
    equipment = equipment.toEnumSet<Equipment>(),
    primaryMuscle = primaryMuscle?.let(MuscleGroup::valueOf),
    assistingMuscles = assistingMuscles.toEnumSet<MuscleGroup>(),
    difficulty = difficulty?.let(TechniqueDifficulty::valueOf),
    technique = technique,
)

private inline fun <reified E : Enum<E>> String.toEnumSet(): Set<E> =
    if (isEmpty()) emptySet() else split(',').map { enumValueOf<E>(it) }.toSet()

private fun CatalogExercise.toEntity() = CustomExerciseEntity(
    id = id,
    name = name,
    type = type.name,
    equipment = equipment.joinToString(",") { it.name },
    primaryMuscle = primaryMuscle?.name,
    assistingMuscles = assistingMuscles.joinToString(",") { it.name },
    difficulty = difficulty?.name,
    technique = technique,
)
