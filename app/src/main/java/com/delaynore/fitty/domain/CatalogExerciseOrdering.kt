package com.delaynore.fitty.domain

import com.delaynore.fitty.data.exercises.CatalogExercise
import java.util.Locale

internal fun normalizeExerciseName(name: String): String =
    name.trim().lowercase(Locale.ROOT).replace('ё', 'е')

internal val catalogExerciseComparator: Comparator<CatalogExercise> =
    compareBy<CatalogExercise>(
        { normalizeExerciseName(it.name) },
        CatalogExercise::isCustom,
        CatalogExercise::name,
        CatalogExercise::id,
    )
