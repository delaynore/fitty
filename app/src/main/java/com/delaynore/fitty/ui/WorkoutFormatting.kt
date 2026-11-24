package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.SetResult
import java.math.BigDecimal

internal fun formatDecimal(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')

internal fun SetResult.formatResult(): String = when (this) {
    is SetResult.WeightAndReps -> "${formatDecimal(weightKg)} кг × $repetitions"
    is SetResult.Reps -> "$repetitions ${type.unit}"
    is SetResult.Duration -> "$seconds ${type.unit}"
    is SetResult.Distance -> "${formatDecimal(meters)} ${type.unit}"
}

internal fun formatRecommendation(sets: Int?, goal: ExerciseGoal?): String? {
    val target = when (goal) {
        is ExerciseGoal.Repetitions -> if (sets == null) "${goal.repetitions} повт." else goal.repetitions.toString()
        is ExerciseGoal.Duration -> "${goal.seconds} с"
        is ExerciseGoal.Distance -> "${formatDecimal(goal.meters)} м"
        null -> null
    }
    return when {
        sets != null && target != null -> "$sets × $target"
        sets != null -> "$sets подх."
        else -> target
    }
}

internal fun ExerciseGoal.inputText(): String = when (this) {
    is ExerciseGoal.Repetitions -> repetitions.toString()
    is ExerciseGoal.Duration -> seconds.toString()
    is ExerciseGoal.Distance -> formatDecimal(meters)
}

internal val ExerciseType.weightLabel: String?
    get() = if (this == ExerciseType.WEIGHT_AND_REPS) "Вес, кг" else null

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

private val positiveDecimalPattern = Regex("[0-9]+([.,][0-9]+)?")
private val wholeNumberPattern = Regex("[0-9]+")

internal fun parseWholeNumber(value: String): Int? =
    value.trim().takeIf(wholeNumberPattern::matches)?.toIntOrNull()

internal fun ExerciseType.parseGoal(value: String): ExerciseGoal? {
    val text = value.trim()
    val integer = parseWholeNumber(text)?.takeIf { it > 0 }
    return when (this) {
        ExerciseType.WEIGHT_AND_REPS, ExerciseType.REPS -> integer?.let { ExerciseGoal.Repetitions(it) }
        ExerciseType.DURATION -> integer?.let { ExerciseGoal.Duration(it) }
        ExerciseType.DISTANCE -> text.takeIf(positiveDecimalPattern::matches)?.replace(',', '.')
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let { ExerciseGoal.Distance(it) }
    }
}
