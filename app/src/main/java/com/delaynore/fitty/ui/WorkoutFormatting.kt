package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseType

internal const val SET_WEIGHT_LABEL = "Вес, кг"
internal val ExerciseType.hasWeightField: Boolean get() = this == ExerciseType.WEIGHT_AND_REPS
internal val ExerciseType.measureLabel: String
    get() = when (this) {
        ExerciseType.WEIGHT_AND_REPS, ExerciseType.REPS -> "Повторения"
        ExerciseType.DURATION -> "Время, с"
        ExerciseType.DISTANCE -> "Дистанция, м"
    }
internal val ExerciseType.measureTag: String
    get() = when (this) {
        ExerciseType.WEIGHT_AND_REPS, ExerciseType.REPS -> "repetitions"
        ExerciseType.DURATION -> "duration"
        ExerciseType.DISTANCE -> "distance"
    }
