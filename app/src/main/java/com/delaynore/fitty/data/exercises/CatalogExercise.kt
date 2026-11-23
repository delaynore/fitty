package com.delaynore.fitty.data.exercises

internal enum class ExerciseType(val label: String, val unit: String) {
    WEIGHT_AND_REPS("Вес и повторы", "кг"),
    REPS("Повторы", "повт."),
    DURATION("Время", "с"),
    DISTANCE("Дистанция", "м"),
}

internal enum class Equipment(val label: String) {
    NONE("Без инвентаря"),
    BAND("Резинка"),
    BARBELL("Штанга"),
    BENCH("Скамья"),
    CABLE("Блочный тренажёр"),
    DUMBBELLS("Гантели"),
    MACHINE("Тренажёр"),
    PLATES("Блины"),
    PULL_UP_BAR("Турник"),
    RACK("Стойка"),
    WEIGHT_BELT("Пояс для отягощений"),
}

internal enum class MuscleGroup(val label: String) {
    ABS("Мышцы живота"),
    BACK("Спина"),
    BICEPS("Бицепс"),
    CALVES("Икры"),
    CHEST("Грудь"),
    FOREARMS("Предплечья"),
    GLUTES("Ягодицы"),
    HAMSTRINGS("Задняя поверхность бедра"),
    HIP_FLEXORS("Сгибатели бедра"),
    LOWER_BACK("Поясница"),
    QUADRICEPS("Квадрицепсы"),
    SHOULDERS("Плечи"),
    TRICEPS("Трицепс"),
    UPPER_TRAPS("Верх трапеций"),
}

internal enum class TechniqueDifficulty(val label: String) {
    BEGINNER("Начальный"),
    INTERMEDIATE("Средний"),
    ADVANCED("Продвинутый"),
}

internal const val CUSTOM_EXERCISE_ID_PREFIX = "custom:"

internal data class CatalogExercise(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val equipment: Set<Equipment> = emptySet(),
    val primaryMuscle: MuscleGroup? = null,
    val assistingMuscles: Set<MuscleGroup> = emptySet(),
    val difficulty: TechniqueDifficulty? = null,
    val technique: String? = null,
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_EXERCISE_ID_PREFIX)
}
