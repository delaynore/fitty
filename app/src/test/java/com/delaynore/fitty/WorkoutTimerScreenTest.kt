package com.delaynore.fitty

import android.provider.Settings
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.ui.workout.SetTimerSignalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = WorkoutTimerTestApplication::class)
class WorkoutTimerScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application get() = ApplicationProvider.getApplicationContext<WorkoutTimerTestApplication>()

    @Before
    fun setBootCount() {
        Settings.Global.putInt(application.contentResolver, Settings.Global.BOOT_COUNT, 1)
    }

    @After
    fun deleteDatabase() {
        application.deleteDatabase()
    }

    @Test
    fun stoppingAfterReturningFromBackgroundLogsOneSetAndCanBeCorrectedInHistory() {
        seedPlank(goal = 40)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startWorkout()
            tap("Старт")
            awaitText("Стоп")
            compose.onNodeWithContentDescription("Запустить таймер отдыха").assertExists()

            val service = Robolectric.buildService(SetTimerSignalService::class.java).create()
            service.get().onStartCommand(null, 0, 1)
            try {
                scenario.moveToState(Lifecycle.State.CREATED)
                application.workoutTimeMillis = 49_999L
                compose.waitForIdle()
                assertEquals(0, application.cues)
                application.workoutTimeMillis = 50_000L
                compose.waitUntil(timeoutMillis = 5_000) { application.cues == 1 }
                application.workoutTimeMillis = 57_000L
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitText("0:47")
                assertEquals(1, application.cues)
                compose.onNodeWithTag("workout-logged-duration-1", useUnmergedTree = true).assertDoesNotExist()
                tap("Стоп")
                awaitLogged(1, "47")
                compose.onNodeWithTag("workout-logged-duration-2", useUnmergedTree = true).assertDoesNotExist()

                compose.onNodeWithContentDescription("Запустить таймер отдыха").performClick()
                awaitText("2:00")
                tap("Завершить")
                tap("Завершить тренировку")
                awaitText("47 с")
                tap("47 с")
                compose.onNodeWithTag("history-set-duration").performTextReplacement("49")

                tap("Сохранить")
                awaitText("49 с")
            } finally {
                service.destroy()
            }
        }
        assertPersistedHistoryDuration(49)
    }

    @Test
    fun stoppingWithinTheFirstSecondLogsZeroAndCanBeSavedAgainInHistory() {
        seedPlank(goal = null)
        ActivityScenario.launch(MainActivity::class.java).use {
            startWorkout()
            tap("Старт")
            awaitText("Стоп")
            application.workoutTimeMillis = 10_999L
            awaitText("0:00")
            tap("Стоп")
            awaitLogged(1, "0")
            tap("Завершить")
            tap("Завершить тренировку")
            awaitText("0 с")
            tap("0 с")
            compose.onNodeWithTag("history-set-duration").performTextReplacement("0")

            tap("Сохранить")
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasTestTag("history-set-duration")).fetchSemanticsNodes().isEmpty()
            }
            awaitText("0 с")
        }
        assertPersistedHistoryDuration(0)
    }

    @Test
    fun restoringAnActiveTimerSchedulesItsFutureCueAgain() {
        seedPlank(goal = 40)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startWorkout()
            tap("Старт")
            awaitText("Стоп")
            scenario.moveToState(Lifecycle.State.CREATED)
            val original = requireNotNull(application.setTimerSignals.pending.value)
            application.setTimerSignals.cancel(original.token)
            application.workoutTimeMillis = 25_000L
            scenario.onActivity {
                it.viewModelStore.clear()
                (it.application as FittyApplication).clearAppContainer()
            }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("0:15")
            val restored = requireNotNull(application.setTimerSignals.pending.value)
            assertEquals(original, restored)
            assertEquals(50_000L, restored.deadlineMillis)
            application.workoutTimeMillis = 50_000L

            tap("Стоп")
            awaitLogged(1, "40")
        }
    }

    @Test
    fun stoppingWithoutAGoalLogsElapsedTimeAfterSavedStateRestoration() {
        seedPlank(goal = null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startWorkout()
            tap("Старт")
            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 25_000L
            scenario.onActivity {
                it.viewModelStore.clear()
                (it.application as FittyApplication).clearAppContainer()
            }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("0:15")
            tap("Стоп")
            awaitLogged(1, "15")
            assertNull(application.setTimerSignals.pending.value)
        }
    }

    @Test
    fun restTimerStartsManuallyKeepsSetRowsInPlaceAndCanBeReset() {
        seedStrength(secondExercise = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            startWorkout()

            val firstRow = compose.onNodeWithTag("workout-set-row-1")
            val idleBounds = firstRow.getUnclippedBoundsInRoot()
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()

            startRestTimerAtKnownTime()
            assertEquals(idleBounds, firstRow.getUnclippedBoundsInRoot())

            tap("Следующее упражнение", description = true)
            awaitText("Тяга верхнего блока")
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertExists()
            tap("Назад", description = true)
            awaitText("Продолжить тренировку")
            tap("Продолжить тренировку")
            awaitText("Тяга верхнего блока")
            tap("Сбросить таймер отдыха", description = true)
            compose.onNodeWithContentDescription("Запустить таймер отдыха").assertExists()
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()
        }
    }

    @Test
    fun restTimerUsesElapsedTimeOnReturnFromBackground() {
        seedStrength()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startWorkout()
            startRestTimerAtKnownTime()

            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 55_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitText("1:15")

            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 130_000L
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasContentDescription("Запустить таймер отдыха")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Сбросить таймер отдыха").assertDoesNotExist()
        }
    }

    @Test
    fun restTimerAndLoggedSetReturnWithSavedStateAfterViewModelsAreLost() {
        seedStrength()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startWorkout()

            startRestTimerAtKnownTime()

            scenario.moveToState(Lifecycle.State.CREATED)
            application.workoutTimeMillis = 55_000L
            // Keep the activity's saved state while discarding the in-memory ViewModels.
            scenario.onActivity {
                it.viewModelStore.clear()
                (it.application as FittyApplication).clearAppContainer()
            }
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)

            awaitText("1:15")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60")
            compose.onNodeWithTag("workout-logged-repetitions-1", useUnmergedTree = true).assertTextEquals("6")
        }
    }

    @Test
    fun twoRemovedSetsCanBeRestoredIndependentlyInReverseOrder() {
        seedStrength(
            secondExercise = true,
            recorded = listOf(SetResult.WeightAndReps(60.0, 6), SetResult.WeightAndReps(65.0, 5)),
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            startWorkout()

            compose.onNodeWithTag("workout-sets").performScrollToIndex(0)

            removeSet(2)
            removeSet(1)
            awaitText("60 кг × 6")
            awaitText("65 кг × 5")
            tap("Следующее упражнение", description = true)
            awaitText("Тяга верхнего блока")
            compose.onNodeWithText("Завершить").assertIsNotEnabled()

            tap("Вернуть подход 1, Жим штанги лёжа", description = true)
            tap("Вернуть подход 2, Жим штанги лёжа", description = true)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodes(hasText("Завершить") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Завершить").assertIsEnabled()
            tap("Все упражнения")
            awaitText("Записано подходов: 2")
            tap("1. Жим штанги лёжа")
            awaitTag("workout-logged-weight-1")
            awaitTag("workout-logged-weight-2")
            compose.onNodeWithTag("workout-logged-weight-1", useUnmergedTree = true).assertTextEquals("60")
            compose.onNodeWithTag("workout-logged-weight-2", useUnmergedTree = true).assertTextEquals("65")
        }
    }

    private fun assertPersistedHistoryDuration(seconds: Int) = runBlocking {
        application.closeDatabase()
        val workouts = application.appContainer(null).workouts
        val id = workouts.history.first().single().id
        val session = requireNotNull(workouts.observeWorkout(id).first())
        assertEquals(SetResult.Duration(seconds), session.exercises.single().sets.single().result)
    }

    private fun seedPlank(goal: Int?) {
        seedWorkout(listOf(TrainingExercise(
            0, "Планка на предплечьях", null, goal?.let { ExerciseGoal.Duration(it) }, type = ExerciseType.DURATION,
        )))
    }

    private fun seedStrength(
        secondExercise: Boolean = false,
        recorded: List<SetResult> = listOf(SetResult.WeightAndReps(60.0, 6)),
    ) {
        val exercises = buildList {
            add(TrainingExercise(0, "Жим штанги лёжа", null, null))
            if (secondExercise) add(TrainingExercise(0, "Тяга верхнего блока", null, null))
        }
        seedWorkout(exercises, recorded)
    }

    private fun seedWorkout(exercises: List<TrainingExercise>, recorded: List<SetResult> = emptyList()) = runBlocking {
        val container = application.appContainer(null)
        val programs = container.programs
        programs.saveProgram(TrainingProgram(0, "Strength", listOf(TrainingDay(0, "A", exercises))))
        val day = requireNotNull(programs.homeProgram.first()).days.single()
        if (recorded.isNotEmpty()) {
            val workouts = container.workouts
            val id = requireNotNull(workouts.startWorkout(day.id))
            val exercise = requireNotNull(workouts.observeWorkout(id).first()).exercises.first()
            recorded.forEachIndexed { index, result ->
                check(workouts.logSet(id, exercise, LoggedSet(index + 1, result)))
            }
        }
    }

    private fun startWorkout() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Начать тренировку") or hasText("Продолжить тренировку"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Начать тренировку") or hasText("Продолжить тренировку"))
            .performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Упражнение 1 из", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitLogged(number: Int, text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag("workout-logged-duration-$number") and hasText(text), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun startRestTimerAtKnownTime() {
        application.workoutTimeMillis = 10_000L
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
}

class WorkoutTimerTestApplication : FittyApplication() {
    override val databaseName = "workout-timer-test.db"

    private val time = MutableStateFlow(10_000L)
    var workoutTimeMillis: Long
        get() = time.value
        set(value) { time.value = value }
    var cues: Int = 0
        private set

    override fun workoutTimerElapsedRealtime() = workoutTimeMillis

    override suspend fun awaitSetTimerDeadline(deadlineMillis: Long) {
        time.first { it >= deadlineMillis }
    }

    override suspend fun playSetTimerSignal() {
        cues++
    }
}
