package com.delaynore.fitty

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.programs.FakeProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.ui.FittyTheme
import com.delaynore.fitty.ui.programs.ExerciseDraft
import com.delaynore.fitty.ui.programs.ProgramEditorEvent
import com.delaynore.fitty.ui.programs.ProgramEditorViewModel
import com.delaynore.fitty.ui.programs.TrainingDayDraft
import com.delaynore.fitty.ui.programs.TrainingDayEditorContent
import com.delaynore.fitty.ui.programs.TrainingDayEditorScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrainingDayEditorContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draggingAtTheBottomEdgeScrollsToTheLastExercise() {
        val exercises = (1..30).map { ExerciseDraft(it.toLong(), "Упражнение $it") }
        val moves = mutableListOf<Pair<Long, Int>>()
        showEditor(exercises) { id, index -> moves.add(id to index) }

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

    @Test
    fun draggingMovesBothRowsBeforeCommittingTheNewPosition() {
        val exercises = (1..3).map { ExerciseDraft(it.toLong(), "Exercise $it") }
        val moves = mutableListOf<Pair<Long, Int>>()
        showEditor(exercises) { id, index -> moves.add(id to index) }
        val first = compose.onNodeWithContentDescription("Переместить «Exercise 1»")
        val last = compose.onNodeWithContentDescription("Переместить «Exercise 3»")
        val firstY = first.fetchSemanticsNode().boundsInRoot.center.y
        val start = last.fetchSemanticsNode().boundsInRoot.center
        val root = compose.onRoot()

        root.performTouchInput {
            down(start)
            moveTo(start.copy(y = firstY))
        }
        assertTrue(last.fetchSemanticsNode().boundsInRoot.center.y < (start.y + firstY) / 2)
        assertTrue(first.fetchSemanticsNode().boundsInRoot.center.y > firstY)
        compose.runOnIdle { assertTrue(moves.isEmpty()) }
        root.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(3L to 0), moves) }
    }

    @Test
    fun editedTargetAppearsOnTheCollapsedExercise() {
        showEditor(listOf(ExerciseDraft(1, "Pulldown", sets = "3", target = "8")))
        compose.onNodeWithText("3 × 8").assertIsDisplayed()
        compose.onNodeWithTag("recommendation-repetitions").assertDoesNotExist()
        compose.onNodeWithText("Pulldown").performClick()
        compose.onNodeWithTag("recommendation-repetitions").performScrollTo().performTextReplacement("10")
        compose.onNodeWithText("3 × 10").performClick()
        compose.onNodeWithTag("recommendation-repetitions").assertDoesNotExist()
        compose.onNodeWithText("3 × 10").assertIsDisplayed()
    }

    @Test
    fun restoredDayScreenSelectsTheRouteDayWithoutRequestingNavigation() {
        val previousDay = TrainingDay(1, "День A", emptyList())
        val routeDay = TrainingDay(2, "День B", emptyList())
        val editor = ProgramEditorViewModel(FakeProgramsRepository()) { 0L }
        editor.openEditor(TrainingProgram(1, "Strength", listOf(previousDay, routeDay)))
        editor.openDay(previousDay.id)
        editor.consumeEvent(ProgramEditorEvent.OpenDay(previousDay.id))
        editor.finishDay()
        assertTrue(editor.uiState.value.showDayErrors)

        compose.setContent {
            FittyTheme {
                TrainingDayEditorScreen(
                    viewModel = editor,
                    dayId = routeDay.id,
                    onMissingDay = { error("The route day is in the draft") },
                    onBack = {},
                    onSettings = {},
                    onPickExercise = {},
                )
            }
        }

        compose.onNodeWithText(routeDay.name).assertIsDisplayed()
        compose.runOnIdle {
            val state = editor.uiState.value
            assertEquals(routeDay.id, state.editingDayId)
            assertFalse(state.showDayErrors)
            assertNull(state.pendingEvent)
        }
    }

    @Test
    fun invalidDurationGoalCanBeCollapsedAndOpenedAgain() {
        val day = TrainingDay(1, "A", listOf(TrainingExercise(1, "Plank", null, null, type = ExerciseType.DURATION)))
        val editor = ProgramEditorViewModel(FakeProgramsRepository()) { 0L }
        editor.openEditor(TrainingProgram(1, "Strength", listOf(day)))
        editor.openDay(day.id)
        var finished = false
        compose.setContent {
            FittyTheme {
                TrainingDayEditorScreen(
                    viewModel = editor,
                    dayId = day.id,
                    onMissingDay = { error("The route day is in the draft") },
                    onBack = { finished = true },
                    onSettings = {},
                    onPickExercise = {},
                )
            }
        }

        compose.onNodeWithText("Plank").performClick()
        val goal = compose.onNodeWithTag("recommendation-duration")
        goal.performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("Сохранить день").performClick()
        compose.runOnIdle { assertFalse(finished) }
        goal.assertExists()
        compose.onNodeWithText("Plank").performClick()
        goal.assertDoesNotExist()
        compose.onNodeWithText("Plank").performClick()
        goal.assertExists()
    }

    private fun showEditor(exercises: List<ExerciseDraft>, onMove: (Long, Int) -> Unit = { _, _ -> }) {
        val day = mutableStateOf(TrainingDayDraft(1, "Day A", exercises))
        compose.setContent {
            FittyTheme {
                Box(Modifier.height(440.dp)) {
                    TrainingDayEditorContent(
                        day = day.value,
                        showErrors = false,
                        onBack = {},
                        onSettings = null,
                        onPickExercise = {},
                        onDayNameChange = {},
                        onChangeExercise = { id, change ->
                            day.value = day.value.copy(exercises = day.value.exercises.map {
                                if (it.id == id) change(it) else it
                            })
                        },
                        onDeleteExercise = {},
                        onMoveExercise = onMove,
                        onSave = {},
                    )
                }
            }
        }
    }
}
