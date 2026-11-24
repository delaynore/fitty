package com.delaynore.fitty.ui.exercises

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delaynore.fitty.data.exercises.BuiltInExercises
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.ui.FittyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogFilterResetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun resetBringsBackResultsForTheCurrentSearch() {
        val catalog = FakeExerciseCatalogRepository()
        catalog.allExercises.value = BuiltInExercises.all.filter {
            it.id in setOf("dumbbell-bench-press", "plank")
        }
        compose.setContent {
            FittyTheme {
                ExerciseCatalogScreen(
                    viewModel = viewModel { ExerciseCatalogViewModel(catalog) },
                    onBack = {},
                    onOpenExercise = {},
                )
            }
        }

        val benchPress = compose.onNodeWithText("Жим гантелей лёжа")
        compose.onNode(hasText("Поиск по названию") and hasSetTextAction())
            .performTextReplacement("жим гантелей лежа")
        benchPress.assertIsDisplayed()

        compose.onNodeWithText("Фильтры").performClick()
        compose.onNodeWithText("Гантели").performScrollTo().performClick()
        compose.onNodeWithText("Применить").performClick()
        compose.onNodeWithText("Упражнения не найдены").assertIsDisplayed()
        benchPress.assertDoesNotExist()

        compose.onNodeWithText("Фильтры (1)").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        benchPress.assertIsDisplayed()
        compose.onNodeWithText("Планка на предплечьях").assertDoesNotExist()
    }
}
