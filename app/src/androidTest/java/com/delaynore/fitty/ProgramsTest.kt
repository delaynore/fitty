package com.delaynore.fitty

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
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
    @SmokeTest
    fun systemBackKeepsDayDraftAndConfirmsLeavingTheProgram() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitText("Создать программу")
            tap("Создать программу")
            replace("program-name", "Силовая")
            tap("Добавить день")
            replace("day-name", "Мой день")
            closeSoftKeyboard()
            pressBack()
            compose.awaitText("Мой день")
            tap("Мой день")
            compose.onNodeWithTag("day-name").assert(hasText("Мой день"))
            pressBack()
            compose.awaitText("Мой день")
            pressBack()
            compose.awaitText("Выйти без сохранения?")
            tap("Продолжить редактирование")
            compose.awaitText("Мой день")
            pressBack()
            tap("Выйти")
            compose.awaitText("Создать программу")
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()

    private fun replace(tag: String, text: String) {
        val field = compose.onNodeWithTag(tag).performScrollTo()
        field.performClick()
        field.performTextReplacement(text)
    }
}
