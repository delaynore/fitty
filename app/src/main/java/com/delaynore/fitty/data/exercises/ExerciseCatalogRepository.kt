package com.delaynore.fitty.data.exercises

import kotlinx.coroutines.flow.Flow

internal interface ExerciseCatalogRepository {
    val allExercises: Flow<List<CatalogExercise>>
    suspend fun create(name: String, type: ExerciseType): CatalogExercise
    suspend fun saveDetails(exercise: CatalogExercise)
}
