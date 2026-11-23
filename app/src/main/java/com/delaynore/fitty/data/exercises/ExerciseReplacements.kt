package com.delaynore.fitty.data.exercises

internal data class ExerciseReplacements(
    val similar: List<CatalogExercise> = emptyList(),
    val sameMuscle: List<CatalogExercise> = emptyList(),
)

// Earlier pairs are listed first for either exercise.
private val similarExercisePairs = listOf(
    "barbell-bench-press" to "dumbbell-bench-press",
    "pull-up" to "weighted-pull-up",
    "push-up" to "incline-push-up",
    "barbell-bench-press" to "chest-press",
    "dumbbell-bench-press" to "chest-press",
    "pull-up" to "lat-pulldown",
    "weighted-pull-up" to "lat-pulldown",
    "barbell-row" to "dumbbell-row",
    "cable-row" to "barbell-row",
    "cable-row" to "dumbbell-row",
    "barbell-squat" to "goblet-squat",
    "bodyweight-squat" to "goblet-squat",
    "bodyweight-squat" to "barbell-squat",
    "reverse-lunge" to "dumbbell-split-squat",
    "glute-bridge" to "barbell-hip-thrust",
    "deadlift" to "romanian-deadlift",
    "dumbbell-curl" to "barbell-curl",
    "dumbbell-curl" to "band-curl",
    "barbell-curl" to "band-curl",
)

internal fun List<CatalogExercise>.replacementsFor(sourceId: String): ExerciseReplacements {
    val byId = associateBy(CatalogExercise::id)
    val source = byId[sourceId] ?: return ExerciseReplacements()
    val similarIds = similarExercisePairs.mapNotNull { (first, second) ->
        when (sourceId) {
            first -> second
            second -> first
            else -> null
        }
    }.toSet() - sourceId
    val sameMuscle = source.primaryMuscle?.let { muscle ->
        byId.values.filter { it.primaryMuscle == muscle && it.id != sourceId && it.id !in similarIds }
    }.orEmpty()
    return ExerciseReplacements(similarIds.mapNotNull(byId::get), sameMuscle)
}
