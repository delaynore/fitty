package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult

private val weightPattern = Regex("[0-9]+([.,][0-9]{1,4})?")
internal data class SetInputErrors(val weight: String? = null, val measure: String? = null)

internal data class SetInput(
    val type: ExerciseType,
    val weight: String = "",
    val measure: String = "",
    val errors: SetInputErrors? = null,
    private val originalWeight: String? = null,
) {
    private val parsedWeight: Double?
        get() = weight.trim().takeIf { it == originalWeight || weightPattern.matches(it) }
            ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    fun validate(): SetInput = copy(errors = SetInputErrors(
        weight = if (type == ExerciseType.WEIGHT_AND_REPS && parsedWeight == null) "Введи вес, например 62,5" else null,
        measure = if (type.parseGoal(measure) != null) null
        else if (type == ExerciseType.DISTANCE) "Введи расстояние больше 0" else "Целое число от 1",
    ))

    fun toLoggedSet(position: Int): LoggedSet? {
        val result = when (val value = type.parseGoal(measure)) {
            is ExerciseGoal.Repetitions -> if (type == ExerciseType.WEIGHT_AND_REPS) {
                SetResult.WeightAndReps(parsedWeight ?: return null, value.repetitions)
            } else {
                SetResult.Reps(value.repetitions)
            }
            is ExerciseGoal.Duration -> SetResult.Duration(value.seconds)
            is ExerciseGoal.Distance -> SetResult.Distance(value.meters)
            null -> return null
        }
        return LoggedSet(position, result)
    }

    fun formatDraft(): String {
        val value = measure.ifBlank { "−" }
        return when (type) {
            ExerciseType.WEIGHT_AND_REPS -> "${weight.ifBlank { "−" }} кг × $value"
            else -> "$value ${type.unit}"
        }
    }
}

internal fun SetResult.toInput(): SetInput = when (this) {
    is SetResult.WeightAndReps -> {
        val formattedWeight = formatDecimal(weightKg)
        SetInput(type, weight = formattedWeight, measure = repetitions.toString(), originalWeight = formattedWeight)
    }
    is SetResult.Reps -> SetInput(type, measure = repetitions.toString())
    is SetResult.Duration -> SetInput(type, measure = seconds.toString())
    is SetResult.Distance -> SetInput(type, measure = formatDecimal(meters))
}
