package com.delaynore.fitty.ui

import java.math.BigDecimal

internal fun formatWeight(weightKg: Double): String =
    BigDecimal.valueOf(weightKg).stripTrailingZeros().toPlainString().replace('.', ',')
