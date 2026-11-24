package com.delaynore.fitty

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProgramEditorRestorationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @After
    fun closeDatabase() {
        ApplicationProvider.getApplicationContext<FittyApplication>().deleteDatabase()
    }

    @Test
    fun restoredDayRouteReturnsHomeWhenTheProgramDraftIsGone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitText("Создать программу")
            tap("Создать программу")
            compose.awaitText("Добавить день")
            tap("Добавить день")
            compose.awaitText("Тренировочный день")
            scenario.onActivity { it.viewModelStore.clear() }
            scenario.recreate()
            compose.awaitText("Создать программу")
        }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()
}
