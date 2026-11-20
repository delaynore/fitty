package com.delaynore.fitty

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
