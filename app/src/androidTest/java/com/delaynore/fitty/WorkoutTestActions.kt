package com.delaynore.fitty

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard

internal fun ComposeTestRule.createAndStartWorkout(secondExercise: String? = null) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasText("Создать программу")).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Создать программу").performClick()
    onNodeWithTag("program-name").performTextInput("Силовая")
    onNodeWithText("Добавить день").performClick()
    onNodeWithTag("day-name").performTextInput("День А")
    addCatalogExercise("Жим штанги лёжа")
    if (secondExercise != null) {
        addCatalogExercise(secondExercise)
    }
    onNodeWithText("Сохранить день").performClick()
    onNodeWithText("Сохранить программу").performClick()
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasText("Начать тренировку")).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Начать тренировку").performScrollTo().performClick()
    awaitEnabledSetEntry(1)
}

internal fun ComposeTestRule.addCatalogExercise(name: String) {
    closeSoftKeyboard()
    onNodeWithText("Добавить упражнение").performScrollTo().performClick()
    searchExerciseCatalog(name)
    onNodeWithContentDescription("Добавить $name").performClick()
}

internal fun ComposeTestRule.searchExerciseCatalog(name: String) {
    onNode(hasText("Поиск по названию") and hasSetTextAction()).performTextReplacement(name)
    closeSoftKeyboard()
}

internal fun ComposeTestRule.openExerciseCard(name: String) {
    onNode(hasText(name) and !hasSetTextAction()).performClick()
}

internal fun ComposeTestRule.logSet(number: Int, weight: String, repetitions: String) {
    awaitEnabledSetEntry(number)
    onNodeWithTag("workout-weight-$number").performScrollTo().performTextInput(weight)
    onNodeWithTag("workout-repetitions-$number").performScrollTo().performTextInput(repetitions)
    onNodeWithContentDescription("Записать подход $number").performClick()
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasTestTag("workout-logged-weight-$number"), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeTestRule.awaitEnabledSetEntry(number: Int) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasTestTag("workout-weight-$number") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
}
