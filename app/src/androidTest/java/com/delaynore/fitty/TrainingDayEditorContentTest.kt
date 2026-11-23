package com.delaynore.fitty

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.delaynore.fitty.ui.FittyTheme
import com.delaynore.fitty.ui.programs.ExerciseDraft
import com.delaynore.fitty.ui.programs.TrainingDayDraft
import com.delaynore.fitty.ui.programs.TrainingDayEditorContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrainingDayEditorContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draggingAtTheBottomEdgeScrollsToTheLastExercise() {
        val exercises = (1..30).map { ExerciseDraft(it.toLong(), "Упражнение $it") }
        val day = TrainingDayDraft(1, "День А", exercises)
        val moves = mutableListOf<Pair<Long, Int>>()
        compose.setContent {
            FittyTheme {
                TrainingDayEditorContent(
                    day = day,
                    showErrors = false,
                    onBack = {},
                    onSettings = null,
                    onPickExercise = {},
                    onDayNameChange = {},
                    onChangeExercise = {},
                    onDeleteExercise = {},
                    onMoveExercise = { id, index -> moves.add(id to index) },
                    onSave = {},
                )
            }
        }

        val first = exercises.first()
        val handle = compose.onNodeWithContentDescription("Переместить «${first.name}»")
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val viewport = compose.onNodeWithTag("day-editor-scroll").fetchSemanticsNode().boundsInRoot
        val addExercise = compose.onAllNodesWithText("Добавить упражнение")
        check(addExercise.fetchSemanticsNodes().none { it.boundsInRoot.height > 0f })
        val root = compose.onRoot()
        root.performTouchInput {
            down(start)
            moveTo(start.copy(y = viewport.bottom - 4f))
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            addExercise.fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }
        }
        root.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(first.id to exercises.lastIndex), moves) }
    }
}
