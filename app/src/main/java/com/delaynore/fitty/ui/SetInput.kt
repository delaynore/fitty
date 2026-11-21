package com.delaynore.fitty.ui

import com.delaynore.fitty.data.workouts.LoggedSet

private val weightPattern = Regex("[0-9]+([.,][0-9]{1,4})?")
private val repetitionsPattern = Regex("[0-9]+")

internal data class SetInputErrors(val weight: String? = null, val repetitions: String? = null)

internal data class SetInput(
    val weight: String = "",
    val repetitions: String = "",
    val errors: SetInputErrors? = null,
    private val originalWeight: String? = null,
) {
    private val parsedWeight: Double?
        get() = weight.trim().takeIf { it == originalWeight || weightPattern.matches(it) }
            ?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    private val parsedRepetitions: Int?
        get() = repetitions.trim().takeIf(repetitionsPattern::matches)?.toIntOrNull()?.takeIf { it > 0 }

    fun validate(): SetInput = copy(errors = SetInputErrors(
        weight = if (parsedWeight == null) "Введи вес, например 62,5" else null,
        repetitions = if (parsedRepetitions == null) "Целое число от 1" else null,
    ))

    fun toLoggedSet(position: Int): LoggedSet? {
        val kg = parsedWeight ?: return null
        val reps = parsedRepetitions ?: return null
        return LoggedSet(position, kg, reps)
    }
}

internal fun LoggedSet.toInput(): SetInput {
    val formattedWeight = formatWeight(weightKg)
    return SetInput(formattedWeight, repetitions.toString(), originalWeight = formattedWeight)
}
