package com.delaynore.fitty.ui.history

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import com.delaynore.fitty.ui.FittyTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HistoryDeletionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theLastSetCanOnlyBeDeletedByConfirmingDeletionOfTheWorkout() {
        val (workouts, id) = runBlocking { completedWorkout() }
        val store = ViewModelStore()
        val viewModel = HistoryViewModel(workouts)
        store.put("history", viewModel)
        try {
            var backCount = 0
            compose.setContent {
                FittyTheme { WorkoutDetailsScreen(id, viewModel, onBack = { backCount++ }) }
            }

            compose.onNodeWithText("Удалить тренировку").performScrollTo().performClick()
            compose.onNodeWithText("Удалить тренировку?").assertExists()
            compose.onNodeWithText("Отмена").performClick()
            compose.onNodeWithText("Удалить тренировку?").assertDoesNotExist()
            compose.onNodeWithText("60 кг × 6").performClick()
            compose.onNodeWithText("Удалить подход").assertDoesNotExist()
            compose.onNodeWithText("Удалить тренировку целиком").performScrollTo().performClick()
            compose.onNodeWithText("Удалить тренировку?").assertExists()
            compose.runOnIdle { assertEquals(listOf(id), viewModel.uiState.value.history?.map { it.id }) }

            compose.onNode(hasAnyAncestor(isDialog()) and hasText("Удалить тренировку")).performClick()
            compose.runOnIdle {
                assertEquals(emptyList<Long>(), viewModel.uiState.value.history?.map { it.id })
                assertEquals(1, backCount)
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}