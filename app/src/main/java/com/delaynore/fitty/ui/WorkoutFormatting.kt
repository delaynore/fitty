package com.delaynore.fitty.ui

import java.math.BigDecimal

internal fun formatWeight(weightKg: Double): String =
    BigDecimal.valueOf(weightKg).stripTrailingZeros().toPlainString().replace('.', ',')

internal fun formatRecommendation(sets: Int?, repetitions: Int?): String? = when {
    sets != null && repetitions != null -> "$sets × $repetitions"
    sets != null -> "$sets подх."
    repetitions != null -> "$repetitions повт."
    else -> null
}
