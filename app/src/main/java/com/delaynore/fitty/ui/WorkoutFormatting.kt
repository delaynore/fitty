package com.delaynore.fitty.ui

import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.domain.formatResult as domainFormatResult
import com.delaynore.fitty.domain.inputText as domainInputText
import com.delaynore.fitty.domain.parseGoal as domainParseGoal

internal fun formatDecimal(value: Double) = com.delaynore.fitty.domain.formatDecimal(value)
internal fun SetResult.formatResult(): String = domainFormatResult()
internal fun formatRecommendation(sets: Int?, goal: ExerciseGoal?) =
    com.delaynore.fitty.domain.formatRecommendation(sets, goal)
internal fun ExerciseGoal.inputText(): String = domainInputText()
internal fun parseWholeNumber(value: String) = com.delaynore.fitty.domain.parseWholeNumber(value)
internal fun ExerciseType.parseGoal(value: String): ExerciseGoal? = domainParseGoal(value)

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
