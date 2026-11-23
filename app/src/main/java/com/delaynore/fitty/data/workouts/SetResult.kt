package com.delaynore.fitty.data.workouts

import com.delaynore.fitty.data.exercises.ExerciseType

internal sealed interface SetResult {
    data class WeightAndReps(val weightKg: Double, val repetitions: Int) : SetResult
    data class Reps(val repetitions: Int) : SetResult
    data class Duration(val seconds: Int) : SetResult
    data class Distance(val meters: Double) : SetResult

    val type: ExerciseType
        get() = when (this) {
            is WeightAndReps -> ExerciseType.WEIGHT_AND_REPS
            is Reps -> ExerciseType.REPS
            is Duration -> ExerciseType.DURATION
            is Distance -> ExerciseType.DISTANCE
        }

    fun isValid(): Boolean = when (this) {
        is WeightAndReps -> weightKg.isFinite() && weightKg >= 0 && repetitions > 0
        is Reps -> repetitions > 0
        is Duration -> seconds > 0
        is Distance -> meters.isFinite() && meters > 0
    }
}
