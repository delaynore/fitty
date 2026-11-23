package com.delaynore.fitty

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomExerciseTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun createsAnExerciseWithoutLosingTheDayDraftAndReusesItOffline() {
        val program = "Силовая"
        val firstDay = "День А"
        val secondDay = "День Б"
        val exercise = "Удержание уголка"
        val earlierExercise = "Жим штанги лёжа"

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("Создать программу")
            compose.onNodeWithTag("program-name").performTextInput(program)
            tap("Добавить день")
            compose.onNodeWithTag("day-name").performTextInput(firstDay)
            compose.addCatalogExercise(earlierExercise)
            tap(earlierExercise)
            compose.onNodeWithTag("recommendation-sets").performScrollTo().performTextInput("3")
            compose.onNodeWithTag("recommendation-repetitions").performScrollTo().performTextInput("8")
            closeSoftKeyboard()
            tap("3 × 8")

            tap("Добавить упражнение")
            compose.searchExerciseCatalog(exercise)
            tap("Создать упражнение")
            compose.onNode(hasText("Название упражнения") and hasSetTextAction()).assertTextContains(exercise)
            tap("Время")
            tap("Сохранить упражнение")

            compose.onNodeWithTag("day-name").assertTextContains(firstDay)
            compose.onNodeWithText("3 × 8").assertIsDisplayed()
            compose.onAllNodesWithText(exercise).assertCountEquals(1)
            tap("Сохранить день")
            compose.onNodeWithTag("program-name").assertTextContains(program)
            tap("Сохранить программу")
            awaitText("Начать тренировку")
        }

        val wasInAirplaneMode = shell("settings get global airplane_mode_on") == "1"
        if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode enable")
        try {
            check(shell("settings get global airplane_mode_on") == "1")
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("Мои программы")
                tap("Мои программы")
                awaitText(program)
                tap(program)
                awaitText("Редактировать программу")
                tap("Редактировать программу")
                tap("Добавить день")
                compose.onNodeWithTag("day-name").performTextInput(secondDay)
                compose.addCatalogExercise(exercise)
                tap("Сохранить день")
                tap("Сохранить программу")
                awaitText(secondDay)
                repeat(2) { compose.onNodeWithContentDescription("Назад").performClick() }
                awaitText(secondDay)
                tap(secondDay)
                tap("Начать тренировку")
                awaitText(exercise)
                awaitText("Упражнение 1 из 1")
                compose.onNodeWithTag("workout-duration-1").assertIsDisplayed()
                compose.onNodeWithTag("workout-weight-1").assertDoesNotExist()
                compose.onNodeWithTag("workout-repetitions-1").assertDoesNotExist()
            }
        } finally {
            if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode disable")
        }
    }

    @Test
    fun fillsInTheDetailsOfACustomExerciseLater() {
        val exercise = "Боковое удержание"
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Создать программу")
            tap("Создать программу")
            tap("Добавить день")
            tap("Добавить упражнение")
            compose.searchExerciseCatalog(exercise)
            tap("Создать упражнение")
            tap("Время")
            tap("Сохранить упражнение")
            awaitText(exercise)

            compose.onNodeWithContentDescription("Настройки").performClick()
            tap("Каталог упражнений")
            compose.searchExerciseCatalog(exercise)
            compose.openExerciseCard(exercise)
            awaitText("Не указана")
            tap("Редактировать сведения")
            compose.onNodeWithText("Не указана", useUnmergedTree = true).performScrollTo().performClick()
            tap("Средний")
            val technique = "Удерживай корпус на одной линии."
            compose.onNode(hasText("Техника выполнения") and hasSetTextAction())
                .performScrollTo().performTextInput(technique)
            closeSoftKeyboard()
            compose.onNodeWithText("Сохранить сведения").performScrollTo().performClick()
            awaitText("Редактировать сведения")
            compose.onNodeWithText("Средний").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(technique).performScrollTo().assertIsDisplayed()
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }
}
