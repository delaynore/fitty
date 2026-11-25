package com.delaynore.fitty.data.exercises

import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeExerciseCatalogRepository : ExerciseCatalogRepository {
    override val allExercises = MutableStateFlow(emptyList<CatalogExercise>())
    private var nextId = 1

    override suspend fun create(name: String, type: ExerciseType): CatalogExercise {
        val exercise = CatalogExercise("custom:${nextId++}", name.trim(), type)
        allExercises.value += exercise
        return exercise
    }

    override suspend fun saveDetails(exercise: CatalogExercise) {
        allExercises.value = allExercises.value.map { if (it.id == exercise.id) exercise else it }
    }
}
