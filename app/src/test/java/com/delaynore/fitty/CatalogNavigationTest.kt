package com.delaynore.fitty

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = CatalogNavigationTestApplication::class)
class CatalogNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun closeDatabase() {
        ApplicationProvider.getApplicationContext<FittyApplication>().deleteDatabase()
    }

    @Test
    fun creatingADurationExerciseReturnsToTheDayWithItsExistingTarget() {
        openDayDraft("День А")
        val earlierExercise = "Жим штанги лёжа"
        tap("Добавить упражнение")
        search(earlierExercise)
        awaitText(earlierExercise)
        compose.onNodeWithContentDescription("Добавить $earlierExercise").performClick()
        tap(earlierExercise)
        compose.onNodeWithTag("recommendation-sets").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("recommendation-repetitions").performScrollTo().performTextReplacement("8")
        tap("3 × 8")

        val exercise = "Удержание уголка"
        tap("Добавить упражнение")
        search(exercise)
        tap("Создать упражнение")
        compose.onNode(hasText("Название упражнения") and hasSetTextAction()).assertTextContains(exercise)
        tap("Время")
        tap("Сохранить упражнение")

        awaitText(exercise)
        compose.onNodeWithTag("day-name").assertTextContains("День А")
        compose.onNodeWithText("3 × 8").assertIsDisplayed()
        compose.onAllNodesWithText(exercise).assertCountEquals(1)
        tap(exercise)
        compose.onNode(hasText("Время, с") and hasSetTextAction()).assertExists()
        tap("Сохранить день")
        compose.onNodeWithTag("program-name").assertTextContains("Силовая")
    }

    @Test
    fun browsingTheSettingsCatalogReturnsToTheSameDraftWithoutAddingTheExercise() {
        openDayDraft("Черновик среды")
        val exercise = "Планка на предплечьях"
        compose.onNodeWithContentDescription("Настройки").performClick()
        tap("Каталог упражнений")
        search("ланка на предп")
        awaitText(exercise)
        tap(exercise)

        awaitText("Время")
        compose.onNodeWithText("Без инвентаря").assertIsDisplayed()
        compose.onNodeWithText("Мышцы живота").assertIsDisplayed()
        compose.onNodeWithText("Ягодицы, плечи").assertIsDisplayed()
        compose.onNodeWithText("Начальный").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Поставь локти под плечи.", substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performScrollTo().performClick()
        awaitText(exercise)
        compose.onNode(hasText("Поиск по названию") and hasSetTextAction()).assertTextContains("ланка на предп")
        back()
        compose.onNodeWithText("О приложении").assertIsDisplayed()
        back()
        compose.onNodeWithTag("day-name").assertTextContains("Черновик среды")
        compose.onNodeWithText(exercise).assertDoesNotExist()
    }

    private fun openDayDraft(name: String) {
        awaitText("Создать программу")
        tap("Создать программу")
        compose.onNodeWithTag("program-name").performTextReplacement("Силовая")
        tap("Добавить день")
        compose.onNodeWithTag("day-name").performTextReplacement(name)
    }

    private fun search(query: String) {
        compose.onNode(hasText("Поиск по названию") and hasSetTextAction()).performTextReplacement(query)
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun back() = compose.onNodeWithContentDescription("Назад").performClick()

    private fun awaitText(text: String) = compose.awaitNode(hasText(text) and !hasSetTextAction())
}

class CatalogNavigationTestApplication : FittyApplication() {
    override val databaseName = "catalog-navigation-test.db"
}
