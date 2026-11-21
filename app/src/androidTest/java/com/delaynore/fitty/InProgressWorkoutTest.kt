package com.delaynore.fitty

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InProgressWorkoutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application get() = ApplicationProvider.getApplicationContext<FittyTestApplication>()

    @Before
    fun resetDatabase() {
        application.resetDatabase()
    }

    @After
    fun resetRestClock() {
        application.restTimeMillis = null
    }

    @Test
    fun restTimerStartsManuallyKeepsSetRowsInPlaceAndCanBeReset() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout(secondExercise = "Тяга")
            compose.logSet(1, "60", "6")
            closeSoftKeyboard()
            val firstRow = compose.onNodeWithTag("workout-set-row-1")
            val idleBounds = firstRow.getUnclippedBoundsInRoot()
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()

            startRestTimerAtKnownTime()
            assertEquals(idleBounds, firstRow.getUnclippedBoundsInRoot())

            tap("Следующее упражнение", description = true)
            awaitText("Тяга")
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertExists()
            tap("Назад", description = true)
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Тяга")
            tap("Сбросить таймер отдыха", description = true)
            compose.onNodeWithContentDescription("Запустить таймер отдыха").assertExists()
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()
        }
    }

    @Test
    fun restTimerUsesElapsedTimeOnReturnFromBackground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.createAndStartWorkout()
            startRestTimerAtKnownTime()

            scenario.moveToState(Lifecycle.State.CREATED)
            application.restTimeMillis = 55_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("1:15")

            scenario.moveToState(Lifecycle.State.CREATED)
            application.restTimeMillis = 130_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasContentDescription("Запустить таймер отдыха")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()
        }
    }

    @Test
    fun restTimerAndLoggedSetReturnWithSavedStateAfterViewModelsAreLost() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.createAndStartWorkout()
            compose.logSet(1, "60", "6")
            closeSoftKeyboard()
            startRestTimerAtKnownTime()

            scenario.moveToState(Lifecycle.State.CREATED)
            application.restTimeMillis = 55_000L
            // Keep the activity's saved state while discarding the in-memory ViewModels.
            scenario.onActivity { it.viewModelStore.clear() }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)

            awaitText("1:15")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60")
            compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertTextEquals("6")
        }
    }

    @Test
    fun recordedAndCorrectedSetSurvivesReopeningAndCanBeFinishedOffline() {
        val wasInAirplaneMode = shell("settings get global airplane_mode_on") == "1"
        if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode enable")
        try {
            check(shell("settings get global airplane_mode_on") == "1")
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.createAndStartWorkout()
                compose.onNodeWithText("Завершить").assertIsNotEnabled()
                enter("workout-weight-1", "60,5")
                enter("workout-repetitions-1", "6")
                compose.onNodeWithContentDescription("Записать подход 1").performClick()
                awaitTag("workout-logged-weight-1")
                tap("Назад", description = true)
                awaitText("Продолжить тренировку")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("Продолжить тренировку")
                tap("Продолжить тренировку")
                awaitTag("workout-logged-weight-1")
                compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60,5")
                tap("Изменить подход 1", description = true)
                awaitTag("workout-edit-weight")
                replace("workout-edit-weight", "62.50001")
                replace("workout-edit-repetitions", "0")
                closeSoftKeyboard()
                tap("Сохранить")
                awaitText("Введи вес, например 62,5")
                awaitText("Целое число от 1")
                replace("workout-edit-weight", "62.5")
                replace("workout-edit-repetitions", "7")
                closeSoftKeyboard()
                tap("Сохранить")
                awaitText("62,5")
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                awaitText("Продолжить тренировку")
                tap("Продолжить тренировку")
                awaitText("62,5")
                compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertTextEquals("7")
                tap("Завершить")
                tap("Завершить тренировку")
                awaitText("62,5 кг × 7")
                tap("Назад", description = true)
                tap("Главная")
                compose.onNodeWithText("Продолжить тренировку").assertDoesNotExist()
            }
        } finally {
            if (!wasInAirplaneMode) shell("cmd connectivity airplane-mode disable")
        }
    }

    @Test
    fun cancelledWorkoutDisappearsAndItsTrainingDayCanBeStartedAgain() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout()
            enter("workout-weight-1", "60")
            enter("workout-repetitions-1", "6")
            tap("Записать подход 1", description = true)
            awaitTag("workout-logged-weight-1")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Записано подходов: 1")
            tap("Отменить тренировку")
            awaitText("Отменить тренировку?")
            tap("Оставить тренировку")
            tap("Отменить тренировку")
            tap("Отменить")
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Отменить тренировку")).fetchSemanticsNodes().isEmpty()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("День А")
            compose.onNodeWithText("Продолжить тренировку").assertDoesNotExist()
            tap("История")
            awaitText("Завершённых тренировок пока нет")
            tap("Главная")
            tap("День А")
            tap("Начать тренировку")
            awaitTag("workout-weight-1")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Завершить").assertIsNotEnabled()
        }
    }

    @Test
    fun twoRemovedSetsCanBeRestoredIndependentlyInReverseOrder() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout(secondExercise = "Тяга")
            compose.logSet(1, "60", "6")
            compose.logSet(2, "65", "5")
            closeSoftKeyboard()
            compose.onNodeWithTag("workout-sets").performScrollToIndex(0)

            removeSet(2)
            removeSet(1)
            awaitText("60 кг × 6")
            awaitText("65 кг × 5")
            tap("Следующее упражнение", description = true)
            awaitText("Тяга")
            compose.onNodeWithText("Завершить").assertIsNotEnabled()

            tap("Вернуть подход 1, Жим лёжа", description = true)
            tap("Вернуть подход 2, Жим лёжа", description = true)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Завершить") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Завершить").assertIsEnabled()
            tap("Все упражнения")
            awaitText("Записано подходов: 2")
            tap("1. Жим лёжа")
            awaitTag("workout-logged-weight-1")
            awaitTag("workout-logged-weight-2")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60")
            compose.onNodeWithTag("workout-logged-weight-2", useUnmergedTree = true).assertTextEquals("65")
        }
    }

    @Test
    fun deletedSetStaysGoneAfterUndoExpiresAndWorkoutReopens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.createAndStartWorkout()
            compose.logSet(1, "60", "6")
            compose.logSet(2, "65", "5")
            closeSoftKeyboard()
            compose.onNodeWithTag("workout-sets").performScrollToIndex(0)
            removeSet(1)
            awaitText("60 кг × 6")
            compose.waitUntil(timeoutMillis = 8_000) {
                compose.onAllNodes(hasContentDescription("Вернуть подход 1, Жим лёжа"))
                    .fetchSemanticsNodes().isEmpty()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitTag("workout-logged-weight-1")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("65")
            compose.logSet(2, "70", "4")
            compose.onNodeWithTag("workout-logged-weight-2", useUnmergedTree = true).assertTextEquals("70")
        }
    }

    private fun startRestTimerAtKnownTime() {
        application.restTimeMillis = 10_000L
        tap("Запустить таймер отдыха", description = true)
        compose.onNodeWithText("2:00").assertExists()
    }

    private fun removeSet(position: Int) {
        compose.onNodeWithTag("workout-set-row-$position").performTouchInput { swipeRight() }
        tap("Удалить подход $position", description = true)
    }

    private fun tap(text: String, description: Boolean = false) {
        val node = if (description) compose.onNodeWithContentDescription(text) else compose.onNodeWithText(text)
        node.performClick()
    }

    private fun enter(tag: String, text: String) = compose.onNodeWithTag(tag).performTextInput(text)

    private fun replace(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        enter(tag, text)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }
}
