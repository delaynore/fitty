package com.delaynore.fitty

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun selectsAProgramAndStartsTheChosenTrainingDay() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("Создать программу")
            replace("program-name", "Основная")
            addDay("Верх", "Жим лёжа")
            addDay("Низ", "Приседания")
            tap("Сохранить программу")

            awaitText("Основная")
            tap("Низ")
            awaitText("Приседания")
            tap("Мои программы")
            compose.onNodeWithContentDescription("Выбрать программу «Основная»")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Выбрана"))
            tap("Основная")
            awaitText("Верх")
            awaitText("Низ")
            action("Назад")
            tap("Создать программу")
            replace("program-name", "Запасная")
            addDay("Тяги", "Становая тяга")
            tap("Сохранить программу")

            awaitText("Запасная")
            action("Выбрать программу «Запасная»")
            awaitText("Становая тяга")
            tap("Мои программы")
            action("Выбрать программу «Основная»")
            awaitText("Жим лёжа")
            tap("Низ")
            tap("Начать тренировку")
            awaitText("Приседания")
            awaitText("Завершить")
        }
    }

    @Test
    fun restoresDeletedDaysInPlaceAndExpiresTheirUndoSeparately() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("Создать программу")
            replace("program-name", "Три дня")
            addDay("Первый день", "Жим")
            addDay("Второй день", "Приседания") {
                compose.onNodeWithText("Добавить упражнение").performScrollTo().performClick()
                replace("exercise-name-2", "Выпады")
            }
            addDay("Третий день", "Тяга")

            deleteDay("Первый день")
            deleteDay("Второй день")
            action("Вернуть день «Второй день»")
            action("Вернуть день «Первый день»")

            val dayNames = listOf("Первый день", "Второй день", "Третий день")
            val positions = dayNames.map { name ->
                compose.onNodeWithTag("training-day-$name").fetchSemanticsNode().boundsInRoot.top
            }
            check(positions.zipWithNext().all { (first, second) -> first < second }) {
                "Restored days are out of order: $positions"
            }

            deleteDay("Первый день")
            Thread.sleep(2_000)
            deleteDay("Второй день")
            compose.waitUntil(timeoutMillis = 6_000) {
                compose.onAllNodes(hasContentDescription("Вернуть день «Первый день»")).fetchSemanticsNodes().isEmpty()
            }
            action("Вернуть день «Второй день»")
            tap("Сохранить программу")

            awaitText("Три дня")
            compose.onNodeWithText("Первый день").assertDoesNotExist()
            awaitText("Приседания")
            awaitText("Выпады")
            tap("Третий день")
            awaitText("Тяга")
        }
    }

    @Test
    fun editingRecommendationsAndExercisesChangesFutureWorkoutsAndKeepsRecordedHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("Создать программу")
            replace("program-name", "Силовая")
            addDay("День жима", "Жим лёжа") {
                compose.onNodeWithText("Рекомендации").performScrollTo().performClick()
                replace("recommendation-sets", "3")
                replace("recommendation-repetitions", "8")
                compose.onNodeWithText("Добавить упражнение").performScrollTo().performClick()
                replace("exercise-name-2", "Разведения")
            }
            tap("Сохранить программу")

            awaitText("День жима")
            tap("Начать тренировку")
            awaitText("Упражнение 1 из 2")
            awaitText("3 × 8")
            compose.logSet(1, "60", "6")
            compose.logSet(2, "60", "6")
            closeSoftKeyboard()
            action("Следующее упражнение")
            awaitText("Разведения")
            compose.onNodeWithText("3 × 8").assertDoesNotExist()
            tap("Завершить")
            tap("Завершить тренировку")
            assertRecordedBenchPress()

            action("Назад")
            tap("Главная")
            awaitText("Начать тренировку")
            tap("Начать тренировку")
            awaitText("Упражнение 1 из 2")
            awaitText("3 × 8")

            action("Назад")
            tap("Мои программы")
            awaitText("Силовая")
            tap("Силовая")
            awaitText("Редактировать программу")
            tap("Редактировать программу")
            tap("День жима")
            replace("exercise-name", "Жим гантелей")
            replace("recommendation-sets", "4")
            replace("recommendation-repetitions", "5")
            closeSoftKeyboard()
            compose.onNodeWithContentDescription("Удалить упражнение 2").performScrollTo().performClick()
            tap("Сохранить день")
            tap("Сохранить программу")
            awaitText("Жим гантелей")
            action("Назад")
            action("Назад")

            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Упражнение 1 из 2")
            awaitText("Жим лёжа")
            awaitText("3 × 8")
            action("Следующее упражнение")
            awaitText("Разведения")
            action("Назад")
            tap("Отменить тренировку")
            tap("Отменить")

            awaitText("Начать тренировку")
            tap("Начать тренировку")
            awaitText("Упражнение 1 из 1")
            awaitText("Жим гантелей")
            awaitText("4 × 5")
            action("Назад")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Продолжить тренировку")
            tap("История")
            awaitText("День жима")
            tap("День жима")
            assertRecordedBenchPress()
            compose.onNodeWithText("Жим гантелей").assertDoesNotExist()
        }
    }

    private fun assertRecordedBenchPress() {
        awaitText("Жим лёжа")
        awaitText("Разведения")
        compose.onAllNodesWithText("60 кг × 6").assertCountEquals(2)
    }

    private fun addDay(name: String, exercise: String, edit: () -> Unit = {}) {
        compose.onNodeWithText("Добавить день").performScrollTo().performClick()
        replace("day-name", name)
        replace("exercise-name", exercise)
        edit()
        tap("Сохранить день")
    }

    private fun deleteDay(name: String) {
        compose.onNodeWithTag("training-day-$name").performScrollTo().performTouchInput { swipeRight() }
        action("Удалить день «$name»")
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun action(description: String) = compose.onNodeWithContentDescription(description).performClick()

    private fun replace(tag: String, text: String) {
        val field = compose.onNodeWithTag(tag).performScrollTo()
        field.performClick()
        field.performTextReplacement(text)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
