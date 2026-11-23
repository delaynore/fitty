package com.delaynore.fitty.data.exercises

internal sealed interface ExerciseGoal {
    data class Repetitions(val repetitions: Int) : ExerciseGoal
    data class Duration(val seconds: Int) : ExerciseGoal
    data class Distance(val meters: Double) : ExerciseGoal

    fun isValidFor(type: ExerciseType): Boolean = when (this) {
        is Repetitions -> type.usesRepetitions && repetitions > 0
        is Duration -> type == ExerciseType.DURATION && seconds > 0
        is Distance -> type == ExerciseType.DISTANCE && meters.isFinite() && meters > 0
    }
}

internal val ExerciseType.usesRepetitions: Boolean
    get() = this == ExerciseType.WEIGHT_AND_REPS || this == ExerciseType.REPS

internal fun ExerciseType.restoreGoal(repetitions: Int?, seconds: Int?, meters: Double?): ExerciseGoal? =
    when (this) {
        ExerciseType.WEIGHT_AND_REPS, ExerciseType.REPS -> repetitions?.let { ExerciseGoal.Repetitions(it) }
        ExerciseType.DURATION -> seconds?.let { ExerciseGoal.Duration(it) }
        ExerciseType.DISTANCE -> meters?.let { ExerciseGoal.Distance(it) }
    }
