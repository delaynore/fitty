package com.delaynore.fitty

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.ui.FittyTheme
import com.delaynore.fitty.ui.SetFields
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.ui.workout.WorkoutRowDrafts
import com.delaynore.fitty.ui.workout.WorkoutUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetFieldsCursorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun insertionInTheMiddleKeepsTheCursorWhenWorkoutStateChanges() {
        val exerciseId = 1L
        val position = 1
        val input = SetInput(ExerciseType.WEIGHT_AND_REPS, weight = "20")
        val state = MutableStateFlow(WorkoutUiState(
            rowsByExercise = mapOf(exerciseId to WorkoutRowDrafts(inputs = mapOf(position to input))),
        ))
        fun updateInput(change: (SetInput) -> SetInput) {
            state.update {
                val rows = it.rows(exerciseId)
                val current = rows.inputs.getValue(position)
                val updatedRows = rows.copy(inputs = mapOf(position to change(current)))
                it.copy(rowsByExercise = mapOf(exerciseId to updatedRows))
            }
        }

        compose.setContent {
            val uiState by state.collectAsStateWithLifecycle()
            FittyTheme {
                SetFields(
                    input = uiState.rows(exerciseId).inputs.getValue(position),
                    onFieldChange = { field, text -> updateInput { it.change(field, text) } },
                    enabled = true,
                    weightModifier = Modifier.testTag("weight"),
                    measureModifier = Modifier,
                    showLabels = false,
                )
            }
        }

        val weight = compose.onNodeWithTag("weight")
        weight.performClick()
        weight.performSemanticsAction(SemanticsActions.SetSelection) { select -> select(1, 1, false) }
        weight.performTextInput("5")
        compose.runOnIdle { updateInput(SetInput::validate) }
        weight.assertTextEquals("250")
        weight.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2)))
        weight.performTextInput("7")
        weight.assertTextEquals("2570")
    }
}
