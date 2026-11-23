package com.delaynore.fitty

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
class ExerciseCatalogBrowseTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun resetDatabase() {
        ApplicationProvider.getApplicationContext<FittyTestApplication>().resetDatabase()
    }

    @Test
    fun settingsCatalogOpensExerciseOfflineAndReturnsToTheSameDayDraft() {
        val wasInAirplaneMode = shell("settings get global airplane_mode_on") == "1"
        if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode enable")
        try {
            check(shell("settings get global airplane_mode_on") == "1")
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Создать программу").performClick()
                compose.onNodeWithText("Добавить день").performClick()
                compose.onNodeWithContentDescription("Настройки").performClick()
                compose.onNodeWithText("Каталог упражнений").performClick()

                compose.onNodeWithText("Поиск по названию").performTextInput("ланка на предп")
                closeSoftKeyboard()
                compose.onNodeWithText("Планка на предплечьях").performClick()
                compose.onNodeWithText("Время").assertIsDisplayed()
                compose.onNodeWithText("Без инвентаря").assertIsDisplayed()
                compose.onNodeWithText("Мышцы живота").assertIsDisplayed()
                compose.onNodeWithText("Ягодицы, плечи").assertIsDisplayed()
                compose.onNodeWithText("Начальный").assertIsDisplayed()
                compose.onNodeWithText("Поставь локти под плечи.", substring = true).performScrollTo().assertIsDisplayed()

                back()
                compose.onNodeWithText("Планка на предплечьях").assertIsDisplayed()
                back()
                compose.onNodeWithText("О приложении").assertIsDisplayed()
                back()
                compose.onNodeWithText("Добавить упражнение").assertIsDisplayed()
                compose.onNodeWithText("Планка на предплечьях").assertDoesNotExist()
            }
        } finally {
            if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode disable")
        }
    }

    @Test
    fun catalogFiltersByMainOrAssistingMuscle() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openCatalog()
            compose.onNodeWithText("Фильтры").performClick()
            compose.onNodeWithText("Любая группа").performClick()
            compose.onNodeWithText("Бицепс").performClick()
            compose.onNodeWithText("Применить").performClick()

            compose.onNodeWithText("Сгибания рук с гантелями").assertIsDisplayed()
            compose.onNodeWithText("Тяга верхнего блока").assertDoesNotExist()

            compose.onNodeWithText("Фильтры", substring = true).performClick()
            compose.onNodeWithText("Вспомогательная").performClick()
            compose.onNodeWithText("Применить").performClick()

            compose.onNodeWithText("Тяга верхнего блока").assertIsDisplayed()
            compose.onNodeWithText("Сгибания рук с гантелями").assertDoesNotExist()
        }
    }

    @Test
    fun catalogSearchRequiresAllAvailableEquipmentAndCanBeClearedOffline() {
        val wasInAirplaneMode = shell("settings get global airplane_mode_on") == "1"
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                openCatalog()
                search("жим гантелей")
                selectDumbbells()

                compose.onNodeWithText("Жим гантелей над головой").assertIsDisplayed()
                compose.onNodeWithText("Жим гантелей лёжа").assertDoesNotExist()
                compose.onNodeWithText("Тяга гантели в наклоне").assertDoesNotExist()

                compose.onNodeWithText("Фильтры (1)").performClick()
                compose.onNodeWithText("Скамья").performClick()
                compose.onNodeWithText("Применить").performClick()
                compose.onNodeWithText("Жим гантелей лёжа").assertIsDisplayed()

                compose.onNodeWithText("Фильтры (1)").performClick()
                compose.onNodeWithText("Сбросить").performClick()
                compose.onNodeWithText("Жим гантелей лёжа").assertIsDisplayed()

                if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode enable")
                check(shell("settings get global airplane_mode_on") == "1")

                search("жим гантелей лежа")
                selectDumbbells()
                compose.onNodeWithText("Упражнения не найдены").assertIsDisplayed()
                compose.onNodeWithText("Фильтры (1)").performClick()
                compose.onNodeWithText("Сбросить").performClick()
                compose.onNodeWithText("Жим гантелей лёжа").assertIsDisplayed()
            }
        } finally {
            if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode disable")
        }
    }

    private fun search(query: String) {
        compose.onNodeWithText("Поиск по названию").performTextReplacement(query)
        closeSoftKeyboard()
    }

    private fun selectDumbbells() {
        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Гантели").performClick()
        compose.onNodeWithText("Применить").performClick()
    }

    private fun openCatalog() {
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Каталог упражнений").performClick()
    }

    private fun back() = compose.onNodeWithContentDescription("Назад").performClick()

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }
}
