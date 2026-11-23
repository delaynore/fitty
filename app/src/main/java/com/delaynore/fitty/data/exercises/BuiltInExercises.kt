package com.delaynore.fitty.data.exercises

import com.delaynore.fitty.data.exercises.Equipment.BAND
import com.delaynore.fitty.data.exercises.Equipment.BARBELL
import com.delaynore.fitty.data.exercises.Equipment.BENCH
import com.delaynore.fitty.data.exercises.Equipment.CABLE
import com.delaynore.fitty.data.exercises.Equipment.DUMBBELLS
import com.delaynore.fitty.data.exercises.Equipment.MACHINE
import com.delaynore.fitty.data.exercises.Equipment.NONE
import com.delaynore.fitty.data.exercises.Equipment.PLATES
import com.delaynore.fitty.data.exercises.Equipment.PULL_UP_BAR
import com.delaynore.fitty.data.exercises.Equipment.RACK
import com.delaynore.fitty.data.exercises.Equipment.WEIGHT_BELT
import com.delaynore.fitty.data.exercises.ExerciseType.DISTANCE
import com.delaynore.fitty.data.exercises.ExerciseType.DURATION
import com.delaynore.fitty.data.exercises.ExerciseType.REPS
import com.delaynore.fitty.data.exercises.ExerciseType.WEIGHT_AND_REPS
import com.delaynore.fitty.data.exercises.MuscleGroup.ABS
import com.delaynore.fitty.data.exercises.MuscleGroup.BACK
import com.delaynore.fitty.data.exercises.MuscleGroup.BICEPS
import com.delaynore.fitty.data.exercises.MuscleGroup.CALVES
import com.delaynore.fitty.data.exercises.MuscleGroup.CHEST
import com.delaynore.fitty.data.exercises.MuscleGroup.FOREARMS
import com.delaynore.fitty.data.exercises.MuscleGroup.GLUTES
import com.delaynore.fitty.data.exercises.MuscleGroup.HAMSTRINGS
import com.delaynore.fitty.data.exercises.MuscleGroup.HIP_FLEXORS
import com.delaynore.fitty.data.exercises.MuscleGroup.LOWER_BACK
import com.delaynore.fitty.data.exercises.MuscleGroup.QUADRICEPS
import com.delaynore.fitty.data.exercises.MuscleGroup.SHOULDERS
import com.delaynore.fitty.data.exercises.MuscleGroup.TRICEPS
import com.delaynore.fitty.data.exercises.MuscleGroup.UPPER_TRAPS
import com.delaynore.fitty.data.exercises.TechniqueDifficulty.ADVANCED
import com.delaynore.fitty.data.exercises.TechniqueDifficulty.BEGINNER
import com.delaynore.fitty.data.exercises.TechniqueDifficulty.INTERMEDIATE

internal object BuiltInExercises {
    // These IDs are stored in training days and workout snapshots. Keep them stable when editing cards.
    val all: List<CatalogExercise> = listOf(
        exercise(
            "plank", "Планка на предплечьях", DURATION, setOf(NONE), ABS, setOf(GLUTES, SHOULDERS), BEGINNER,
            "Поставь локти под плечи. Вытяни ноги, напряги живот и ягодицы. Держи корпус ровно и продолжай дышать.",
        ),
        exercise(
            "running", "Бег", DISTANCE, setOf(NONE), QUADRICEPS, setOf(GLUTES, CALVES, HAMSTRINGS), BEGINNER,
            "Начни с лёгкого темпа. Держи плечи расслабленными и ставь стопу близко к проекции корпуса.",
        ),
        exercise(
            "push-up", "Отжимания", REPS, setOf(NONE), CHEST, setOf(TRICEPS, SHOULDERS, ABS), BEGINNER,
            "Поставь ладони чуть шире плеч. Сохраняй ровную линию от головы до пяток, опусти грудь и оттолкнись от пола.",
        ),
        exercise(
            "incline-push-up", "Отжимания от опоры", REPS, setOf(BENCH), CHEST, setOf(TRICEPS, SHOULDERS), BEGINNER,
            "Поставь ладони на устойчивую опору. Согни руки и подведи грудь к опоре. Выпрями руки, сохраняя корпус ровным.",
        ),
        exercise(
            "barbell-bench-press", "Жим штанги лёжа", WEIGHT_AND_REPS, setOf(BARBELL, BENCH, RACK), CHEST,
            setOf(TRICEPS, SHOULDERS), INTERMEDIATE,
            "Поставь стопы на пол и сведи лопатки. Опусти гриф к нижней части груди и выжми его вверх. Используй страховочные упоры или попроси подстраховать.",
        ),
        exercise(
            "dumbbell-bench-press", "Жим гантелей лёжа", WEIGHT_AND_REPS, setOf(DUMBBELLS, BENCH), CHEST,
            setOf(TRICEPS, SHOULDERS), INTERMEDIATE,
            "Поставь стопы на пол и сведи лопатки. Опусти гантели по сторонам груди и выжми их вверх, удерживая кисти над локтями.",
        ),
        exercise(
            "chest-press", "Жим от груди в тренажёре", WEIGHT_AND_REPS, setOf(MACHINE), CHEST,
            setOf(TRICEPS, SHOULDERS), BEGINNER,
            "Отрегулируй сиденье так, чтобы рукоятки были на уровне груди. Прижми спину к опоре, выжми рукоятки и плавно верни их.",
        ),
        exercise(
            "dumbbell-fly", "Разведение гантелей лёжа", WEIGHT_AND_REPS, setOf(DUMBBELLS, BENCH), CHEST,
            setOf(SHOULDERS), INTERMEDIATE,
            "Держи локти слегка согнутыми. Разводи руки по дуге до комфортного положения, затем сведи гантели над грудью.",
        ),
        exercise(
            "pull-up", "Подтягивания", REPS, setOf(PULL_UP_BAR), BACK, setOf(BICEPS, FOREARMS), INTERMEDIATE,
            "Возьмись за перекладину хватом чуть шире плеч. Веди локти вниз, пока подбородок не окажется над перекладиной. Опустись без раскачки.",
        ),
        exercise(
            "weighted-pull-up", "Подтягивания с отягощением", WEIGHT_AND_REPS, setOf(PULL_UP_BAR, WEIGHT_BELT, PLATES),
            BACK, setOf(BICEPS, FOREARMS), ADVANCED,
            "Закрепи груз на поясе. Подтянись без рывка до уровня, где подбородок проходит перекладину. Опустись плавно. Записывай вес добавленного груза.",
        ),
        exercise(
            "lat-pulldown", "Тяга верхнего блока", WEIGHT_AND_REPS, setOf(CABLE), BACK, setOf(BICEPS, FOREARMS), BEGINNER,
            "Зафиксируй бёдра под валиками. Веди локти вниз и подтяни рукоятку к верхней части груди. Плавно выпрями руки.",
        ),
        exercise(
            "cable-row", "Тяга нижнего блока", WEIGHT_AND_REPS, setOf(CABLE), BACK, setOf(BICEPS, SHOULDERS), BEGINNER,
            "Упрись стопами в платформу и слегка согни колени. Подтяни рукоятку к животу, ведя локти назад. Верни руки вперёд без рывка корпусом.",
        ),
        exercise(
            "barbell-row", "Тяга штанги в наклоне", WEIGHT_AND_REPS, setOf(BARBELL), BACK,
            setOf(BICEPS, FOREARMS, LOWER_BACK), INTERMEDIATE,
            "Отведи таз назад и слегка согни колени. Удерживая наклон корпуса, подтяни гриф к животу и плавно опусти его.",
        ),
        exercise(
            "dumbbell-row", "Тяга гантели в наклоне", WEIGHT_AND_REPS, setOf(DUMBBELLS, BENCH), BACK,
            setOf(BICEPS, FOREARMS), BEGINNER,
            "Опрись коленом и ладонью на скамью. Веди локоть рабочей руки к тазу и опускай гантель без поворота корпуса.",
        ),
        exercise(
            "bodyweight-squat", "Приседания без веса", REPS, setOf(NONE), QUADRICEPS, setOf(GLUTES, ABS), BEGINNER,
            "Поставь стопы примерно на ширине плеч. Согни колени и тазобедренные суставы, направляя колени вдоль носков. Поднимись с опорой на всю стопу.",
        ),
        exercise(
            "barbell-squat", "Приседания со штангой", WEIGHT_AND_REPS, setOf(BARBELL, RACK), QUADRICEPS,
            setOf(GLUTES, ABS, LOWER_BACK), ADVANCED,
            "Уложи гриф на верх спины и напряги корпус. Присядь, направляя колени вдоль носков, затем встань с опорой на всю стопу. Выставь страховочные упоры.",
        ),
        exercise(
            "goblet-squat", "Приседания с гантелью у груди", WEIGHT_AND_REPS, setOf(DUMBBELLS), QUADRICEPS,
            setOf(GLUTES, ABS), BEGINNER,
            "Держи гантель у груди. Присядь между бёдрами, сохраняя опору на всю стопу. Вернись в стойку.",
        ),
        exercise(
            "reverse-lunge", "Выпады назад", REPS, setOf(NONE), QUADRICEPS, setOf(GLUTES, ABS), BEGINNER,
            "Шагни назад и согни оба колена. Колено передней ноги направляй вдоль носка. Оттолкнись передней стопой и вернись в стойку.",
        ),
        exercise(
            "dumbbell-split-squat", "Сплит-приседания с гантелями", WEIGHT_AND_REPS, setOf(DUMBBELLS), QUADRICEPS,
            setOf(GLUTES, ABS), INTERMEDIATE,
            "Поставь одну ногу впереди и держи гантели по бокам. Опусти заднее колено к полу. Поднимись, опираясь на переднюю стопу.",
        ),
        exercise(
            "leg-press", "Жим ногами", WEIGHT_AND_REPS, setOf(MACHINE), QUADRICEPS, setOf(GLUTES, HAMSTRINGS), BEGINNER,
            "Поставь стопы на платформу и прижми таз к сиденью. Согни колени до положения, в котором таз остаётся на опоре. Выжми платформу без рывка.",
        ),
        exercise(
            "leg-extension", "Разгибание ног в тренажёре", WEIGHT_AND_REPS, setOf(MACHINE), QUADRICEPS,
            emptySet(), BEGINNER,
            "Совмести колени с осью тренажёра и поставь валик над стопами. Разогни колени и плавно опусти валик.",
        ),
        exercise(
            "wall-sit", "Стульчик у стены", DURATION, setOf(NONE), QUADRICEPS, setOf(GLUTES), BEGINNER,
            "Прижми спину к стене, вынеси стопы вперёд и опустись до удобной высоты. Удерживай положение с опорой на обе стопы.",
        ),
        exercise(
            "deadlift", "Становая тяга", WEIGHT_AND_REPS, setOf(BARBELL), GLUTES,
            setOf(HAMSTRINGS, QUADRICEPS, LOWER_BACK, FOREARMS), ADVANCED,
            "Поставь стопы под грифом и возьмись за него. Напряги корпус, выпрями ноги и таз, ведя гриф близко к телу. Верни штангу на пол под контролем.",
        ),
        exercise(
            "romanian-deadlift", "Румынская тяга со штангой", WEIGHT_AND_REPS, setOf(BARBELL), HAMSTRINGS,
            setOf(GLUTES, LOWER_BACK, FOREARMS), INTERMEDIATE,
            "Слегка согни колени и отводи таз назад. Опускай гриф близко к ногам, пока держишь спину ровной. Подай таз вперёд и выпрямись.",
        ),
        exercise(
            "glute-bridge", "Ягодичный мостик", REPS, setOf(NONE), GLUTES, setOf(HAMSTRINGS, ABS), BEGINNER,
            "Ляг на спину и поставь стопы на пол. Подними таз за счёт ягодиц, сохраняя рёбра опущенными, затем плавно опусти таз.",
        ),
        exercise(
            "barbell-hip-thrust", "Ягодичный мост со штангой", WEIGHT_AND_REPS, setOf(BARBELL, BENCH), GLUTES,
            setOf(HAMSTRINGS, ABS), INTERMEDIATE,
            "Опрись лопатками на устойчивую скамью и уложи гриф через накладку на таз. Упрись стопами в пол, подними таз и плавно опусти.",
        ),
        exercise(
            "leg-curl", "Сгибание ног в тренажёре", WEIGHT_AND_REPS, setOf(MACHINE), HAMSTRINGS, setOf(CALVES), BEGINNER,
            "Совмести колени с осью тренажёра, а валик расположи над пятками. Согни колени и плавно разогни их, удерживая таз на опоре.",
        ),
        exercise(
            "calf-raise", "Подъём на носки", REPS, setOf(NONE), CALVES, emptySet(), BEGINNER,
            "Поднимись на носки и задержись на мгновение. Плавно опусти пятки. Для равновесия держись за опору.",
        ),
        exercise(
            "dumbbell-curl", "Сгибания рук с гантелями", WEIGHT_AND_REPS, setOf(DUMBBELLS), BICEPS, setOf(FOREARMS), BEGINNER,
            "Прижми локти к бокам и поверни ладони вперёд. Согни руки без раскачки корпуса, затем медленно опусти гантели.",
        ),
        exercise(
            "barbell-curl", "Сгибания рук со штангой", WEIGHT_AND_REPS, setOf(BARBELL), BICEPS, setOf(FOREARMS), BEGINNER,
            "Держи гриф хватом снизу. Согни руки, оставляя локти рядом с корпусом. Плавно опусти гриф.",
        ),
        exercise(
            "band-curl", "Сгибания рук с резинкой", REPS, setOf(BAND), BICEPS, setOf(FOREARMS), BEGINNER,
            "Наступи на середину резинки и возьми концы. Согни руки, не двигая локти вперёд, затем медленно ослабь натяжение.",
        ),
        exercise(
            "triceps-pushdown", "Разгибание рук на блоке", WEIGHT_AND_REPS, setOf(CABLE), TRICEPS, setOf(FOREARMS), BEGINNER,
            "Прижми локти к бокам. Разогни руки, направляя рукоятку вниз, затем плавно согни их, не меняя положения локтей.",
        ),
        exercise(
            "dumbbell-shoulder-press", "Жим гантелей над головой", WEIGHT_AND_REPS, setOf(DUMBBELLS), SHOULDERS,
            setOf(TRICEPS, ABS), INTERMEDIATE,
            "Подними гантели к плечам. Выжми их вверх, сохраняя рёбра над тазом, затем плавно опусти к плечам.",
        ),
        exercise(
            "dumbbell-lateral-raise", "Подъём гантелей в стороны", WEIGHT_AND_REPS, setOf(DUMBBELLS), SHOULDERS,
            setOf(UPPER_TRAPS), BEGINNER,
            "Слегка согни локти и подними руки в стороны примерно до уровня плеч. Не раскачивай корпус и плавно опусти гантели.",
        ),
        exercise(
            "dead-bug", "Мёртвый жук", REPS, setOf(NONE), ABS, setOf(HIP_FLEXORS), BEGINNER,
            "Ляг на спину, руки подними, а ноги согни под прямым углом. Вытяни противоположные руку и ногу без прогиба в пояснице, верни и поменяй сторону.",
        ),
        exercise(
            "side-plank", "Боковая планка", DURATION, setOf(NONE), ABS, setOf(GLUTES, SHOULDERS), INTERMEDIATE,
            "Поставь локоть под плечо и подними таз. Выстрой плечи, таз и стопы в линию. Удерживай положение и дыши ровно.",
        ),
    ).sortedBy { it.name.lowercase().replace('ё', 'е') }

    private val byId = all.associateBy(CatalogExercise::id)

    operator fun get(id: String): CatalogExercise? = byId[id]
}

private fun exercise(
    id: String,
    name: String,
    type: ExerciseType,
    equipment: Set<Equipment>,
    primary: MuscleGroup,
    assisting: Set<MuscleGroup>,
    difficulty: TechniqueDifficulty,
    technique: String,
) = CatalogExercise(id, name, type, equipment, primary, assisting, difficulty, technique)
