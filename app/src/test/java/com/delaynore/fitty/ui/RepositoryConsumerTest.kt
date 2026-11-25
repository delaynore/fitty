package com.delaynore.fitty.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.data.programs.FakeProgramsRepository
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.UpdateAttempt
import com.delaynore.fitty.data.updates.UpdateAttemptStore
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.updates.UpdateSessionManager
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.domain.NoopWorkoutTimerStore
import com.delaynore.fitty.domain.SetInput
import com.delaynore.fitty.domain.SetInputErrors
import com.delaynore.fitty.domain.SetInputField
import com.delaynore.fitty.domain.inputText
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.domain.StorageException
import com.delaynore.fitty.ui.exercises.ExerciseCatalogViewModel
import com.delaynore.fitty.ui.exercises.ExerciseEditorViewModel
import com.delaynore.fitty.ui.exercises.ExerciseEditorEvent
import com.delaynore.fitty.ui.history.HistoryViewModel
import com.delaynore.fitty.ui.history.WorkoutDetailsViewModel
import com.delaynore.fitty.ui.home.HomeViewModel
import com.delaynore.fitty.ui.home.HomeEvent
import com.delaynore.fitty.ui.programs.ProgramDetailsViewModel
import com.delaynore.fitty.ui.programs.ProgramEditorViewModel
import com.delaynore.fitty.ui.programs.ProgramEditorEvent
import com.delaynore.fitty.ui.programs.ProgramsViewModel
import com.delaynore.fitty.ui.programs.ProgramsEvent
import com.delaynore.fitty.ui.settings.InstalledApp
import com.delaynore.fitty.ui.settings.UpdateCoordinator
import com.delaynore.fitty.ui.settings.UpdateCheckState
import com.delaynore.fitty.ui.settings.UpdateInstallState
import com.delaynore.fitty.domain.SetTimerSignalScheduler
import com.delaynore.fitty.ui.workout.ActiveWorkoutState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryConsumerTest {
    @Test
    fun homeStartsTheSelectedProgramsChosenDayAndObservesCancellation() = runTest {
        val programs = FakeProgramsRepository()
        val days = listOf(TrainingDay(4, "A", emptyList()), TrainingDay(5, "B", emptyList()))
        val program = TrainingProgram(1, "Strength", days)
        programs.saveProgram(TrainingProgram(2, "Warmup", listOf(days.first())))
        programs.saveProgram(program)
        val workouts = FakeWorkoutsRepository(days.associate { it.id to workout(dayName = it.name) })
        var cancelledId: Long? = null
        withViewModel({ HomeViewModel(programs, workouts) { cancelledId = it } }) { home ->
            assertTrue(home.uiState.value.loading)
            backgroundScope.launch { home.uiState.collect() }
            runCurrent()
            assertEquals("Warmup", home.uiState.value.program?.name)
            val programsList = ProgramsViewModel(programs)
            try {
                backgroundScope.launch { programsList.uiState.collect() }
                runCurrent()
                programsList.selectProgram(program.id)
                runCurrent()
                assertEquals(ProgramsEvent.ProgramSelected, programsList.uiState.value.pendingEvent)
                programsList.consumeEvent(ProgramsEvent.ProgramSelected)
                runCurrent()
                assertNull(programsList.uiState.value.pendingEvent)
                assertTrue(programsList.uiState.value.programs.orEmpty().single { it.id == program.id }.selected)
                assertEquals(program, home.uiState.value.program)
            } finally {
                programsList.viewModelScope.cancel()
            }
            home.selectDay(5)
            runCurrent()
            assertEquals(5L, home.uiState.value.selectedDayId)
            programs.selectProgram(2)
            runCurrent()
            assertNull(home.uiState.value.selectedDayId)
            programs.selectProgram(program.id)
            runCurrent()
            home.selectDay(5)
            runCurrent()
            assertEquals(5L, home.uiState.value.selectedDayId)
            home.startWorkout()
            runCurrent()
            val active = requireNotNull(home.uiState.value.inProgressWorkout)
            assertEquals("B", active.dayName)
            assertEquals(HomeEvent.OpenWorkout(active.id), home.uiState.value.pendingEvent)
            home.consumeEvent(HomeEvent.OpenWorkout(active.id + 1))
            runCurrent()
            assertEquals(HomeEvent.OpenWorkout(active.id), home.uiState.value.pendingEvent)
            home.consumeEvent(HomeEvent.OpenWorkout(active.id))
            runCurrent()
            assertNull(home.uiState.value.pendingEvent)
            home.confirmCancelWorkout(active.id)
            runCurrent()
            assertEquals(active.id, home.uiState.value.confirmingCancelWorkoutId)
            home.cancelWorkout(active.id)
            runCurrent()
            assertNull(home.uiState.value.inProgressWorkout)
            assertEquals(active.id, cancelledId)
            assertNull(home.uiState.value.confirmingCancelWorkoutId)
        }
    }

    @Test
    fun deletedDaysReturnToTheirOriginalPositionsAndExpireIndependently() = runTest {
        val programs = FakeProgramsRepository()
        val squat = TrainingExercise(8, "Squat", null, null)
        val lunge = TrainingExercise(9, "Lunge", null, null)
        val days = listOf(
            TrainingDay(1, "A", listOf(squat)),
            TrainingDay(2, "B", listOf(squat, lunge)),
            TrainingDay(3, "C", listOf(lunge)),
        )
        val program = TrainingProgram(1, "Strength", days)
        programs.saveProgram(program)
        withViewModel({ ProgramEditorViewModel(programs) { testScheduler.currentTime } }) { editor ->
            editor.openEditor(program)
            editor.removeDay(1)
            editor.removeDay(2)
            assertEquals(listOf("C"), editor.uiState.value.visibleDays.map { it.name })
            editor.restoreDay(2)
            editor.restoreDay(1)
            assertEquals(listOf("A", "B", "C"), editor.uiState.value.visibleDays.map { it.name })

            advanceTimeBy(1)
            editor.removeDay(1)
            advanceTimeBy(2_000)
            editor.removeDay(2)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(listOf("B"), editor.uiState.value.removedDays.map { it.name })
            editor.restoreDay(1)
            editor.restoreDay(2)
            editor.saveProgram()
            runCurrent()
            assertEquals(days.drop(1), programs.observeProgram(program.id).first()?.days)

            editor.openEditor(program)
            editor.removeDay(1)
            advanceTimeBy(2_000)
            editor.removeDay(2)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(listOf("B"), editor.uiState.value.removedDays.map { it.name })
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(editor.uiState.value.removedDays.isEmpty())
            assertEquals(listOf("C"), editor.uiState.value.visibleDays.map { it.name })
        }
    }

    @Test
    fun programDetailsFollowTheOpenIdAndSaveReadsTheLatestDraft() = runTest {
        val programs = FakeProgramsRepository()
        val day = TrainingDay(4, "A", listOf(TrainingExercise(8, "Squat", recommendedSets = null, goal = null)))
        val first = TrainingProgram(1, "First", listOf(day))
        val second = TrainingProgram(2, "Second", listOf(day))
        programs.saveProgram(first)
        programs.saveProgram(second)
        val observed = mutableSetOf<Long>()
        var allProgramsObserved = false
        val repository = object : ProgramsRepository by programs {
            override val allPrograms = programs.allPrograms.onStart { allProgramsObserved = true }
            override fun observeProgram(id: Long) = programs.observeProgram(id)
                .onStart { observed.add(id) }
                .onCompletion { observed.remove(id) }
        }
        withViewModel({ ProgramDetailsViewModel(second.id, repository) }) { details ->
            assertFalse(allProgramsObserved)
            assertTrue(observed.isEmpty())
            details.uiState.test {
                assertNull(awaitItem().details)
                assertEquals(second, awaitItem().details)
                assertEquals(setOf(second.id), observed)
            }
        }
        withViewModel({ ProgramEditorViewModel(repository) { testScheduler.currentTime } }) { editor ->
            runCurrent()
            assertFalse(allProgramsObserved)
            editor.openEditor(first)
            editor.changeName("Strength")
            editor.openDay(day.id)
            editor.changeDayName("Legs")
            editor.changeExercise(8) { it.copy(sets = "3") }
            editor.changeExercise(8) { it.copy(target = "8") }
            editor.saveProgram()
            runCurrent()
            val saved = requireNotNull(programs.observeProgram(first.id).first())
            assertEquals("Strength", saved.name)
            val savedDay = saved.days.single()
            assertEquals("Legs", savedDay.name)
            assertEquals(3, savedDay.exercises.single().recommendedSets)
            assertEquals("8", savedDay.exercises.single().goal?.inputText())
            assertTrue(editor.uiState.value.editing)
            assertEquals(ProgramEditorEvent.Saved, editor.uiState.value.pendingEvent)
            editor.consumeEvent(ProgramEditorEvent.OpenDay(day.id))
            assertEquals(ProgramEditorEvent.Saved, editor.uiState.value.pendingEvent)
            editor.consumeEvent(ProgramEditorEvent.Saved)
            assertNull(editor.uiState.value.pendingEvent)

            editor.openDay(day.id)
            assertEquals(ProgramEditorEvent.OpenDay(day.id), editor.uiState.value.pendingEvent)
            editor.consumeEvent(ProgramEditorEvent.OpenDay(day.id))
            assertNull(editor.uiState.value.pendingEvent)

            editor.finishDay()
            assertEquals(ProgramEditorEvent.DayFinished, editor.uiState.value.pendingEvent)
            editor.consumeEvent(ProgramEditorEvent.DayFinished)
            assertNull(editor.uiState.value.pendingEvent)
        }
    }

    @Test
    fun catalogRendersLoadingAndRepositoryUpdates() = runTest {
        val catalog = FakeExerciseCatalogRepository()
        withViewModel({ ExerciseCatalogViewModel(catalog) }) { viewModel ->
            viewModel.uiState.test {
                assertNull(awaitItem().exercises)
                assertEquals(emptyList<CatalogExercise>(), awaitItem().exercises)
                val created = catalog.create("Планка", ExerciseType.DURATION)
                assertEquals(listOf(created), awaitItem().exercises)
            }
        }
    }

    @Test
    fun catalogKeepsItsRoomSubscriptionForFiveSeconds() = runTest {
        val catalog = FakeExerciseCatalogRepository()
        val exercise = catalog.create("Plank", ExerciseType.DURATION)
        withViewModel({ ExerciseCatalogViewModel(catalog) }) { viewModel ->
            val firstCollection = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertEquals(1, catalog.allExercises.subscriptionCount.value)
            assertEquals(listOf(exercise), viewModel.uiState.value.exercises)

            firstCollection.cancel()
            advanceTimeBy(4_000)
            runCurrent()
            assertEquals(1, catalog.allExercises.subscriptionCount.value)
            assertEquals(listOf(exercise), viewModel.uiState.value.exercises)

            val secondCollection = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            assertEquals(1, catalog.allExercises.subscriptionCount.value)
            assertEquals(listOf(exercise), viewModel.uiState.value.exercises)

            secondCollection.cancel()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(0, catalog.allExercises.subscriptionCount.value)
        }
    }

    @Test
    fun exerciseEditorSavesTheLatestNameAndSelectedType() = runTest {
        val catalog = FakeExerciseCatalogRepository()
        withViewModel({ ExerciseEditorViewModel(catalog) }) { editor ->
            editor.open("Планка")
            editor.selectType(ExerciseType.DURATION)
            editor.changeName("Боковая планка")
            editor.save()
            runCurrent()
            val saved = catalog.allExercises.value.single()
            assertEquals("Боковая планка", saved.name)
            assertEquals(ExerciseType.DURATION, saved.type)
            assertEquals(ExerciseEditorEvent.Saved(saved), editor.uiState.value.pendingEvent)
            editor.consumeEvent(ExerciseEditorEvent.Saved(saved.copy(name = "Other")))
            assertEquals(ExerciseEditorEvent.Saved(saved), editor.uiState.value.pendingEvent)
            editor.consumeEvent(ExerciseEditorEvent.Saved(saved))
            assertNull(editor.uiState.value.pendingEvent)
        }
    }

    @Test
    fun exerciseEditorRemainsEditableWhenStorageFails() = runTest {
        val stored = FakeExerciseCatalogRepository()
        val failing = object : ExerciseCatalogRepository by stored {
            override suspend fun create(name: String, type: ExerciseType): CatalogExercise =
                throw StorageException(IllegalStateException("storage failed"))
        }
        withViewModel({ ExerciseEditorViewModel(failing) }) { editor ->
            editor.open("Планка")
            editor.selectType(ExerciseType.DURATION)
            editor.save()
            runCurrent()

            val draft = requireNotNull(editor.uiState.value.draft)
            assertFalse(draft.saving)
            assertEquals("Планка", draft.name)
            assertEquals("Не удалось сохранить упражнение", draft.error)
            assertNull(editor.uiState.value.pendingEvent)
        }
    }

    @Test
    fun exerciseEditorDoesNotLetAnOldSaveChangeANewDraft() = runTest {
        val stored = FakeExerciseCatalogRepository()
        val saveStarted = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        val delayed = object : ExerciseCatalogRepository by stored {
            override suspend fun create(name: String, type: ExerciseType): CatalogExercise {
                saveStarted.complete(Unit)
                finishSave.await()
                return stored.create(name, type)
            }
        }
        withViewModel({ ExerciseEditorViewModel(delayed) }) { editor ->
            editor.open("Plank")
            editor.selectType(ExerciseType.DURATION)
            editor.save()
            saveStarted.await()

            editor.open("Squat")
            editor.selectType(ExerciseType.REPS)
            finishSave.complete(Unit)
            runCurrent()

            val draft = requireNotNull(editor.uiState.value.draft)
            assertEquals("Squat", draft.name)
            assertFalse(draft.saving)
            assertNull(draft.error)
            assertNull(editor.uiState.value.pendingEvent)
        }
    }

    @Test
    fun exerciseEditorWaitsForTheSavedEventToBeConsumedBeforeSavingAgain() = runTest {
        val stored = FakeExerciseCatalogRepository()
        var createCount = 0
        val counting = object : ExerciseCatalogRepository by stored {
            override suspend fun create(name: String, type: ExerciseType): CatalogExercise {
                createCount += 1
                return stored.create(name, type)
            }
        }
        withViewModel({ ExerciseEditorViewModel(counting) }) { editor ->
            editor.open("Plank")
            editor.selectType(ExerciseType.DURATION)
            editor.save()
            runCurrent()
            val event = requireNotNull(editor.uiState.value.pendingEvent)

            editor.save()
            runCurrent()
            assertEquals(1, createCount)

            editor.consumeEvent(event)
            editor.save()
            runCurrent()
            assertEquals(2, createCount)
        }
    }

    @Test
    fun workoutMergesDraftFieldsBeforeSavingAndObservesReplacementsOnlyWhileOpen() = runTest {
        val exercise = WorkoutExercise(8, "Жим", emptyList(), catalogExerciseId = "barbell-bench-press")
        val replacement = CatalogExercise("dumbbell-bench-press", "Жим гантелей", ExerciseType.WEIGHT_AND_REPS)
        val workouts = FakeWorkoutsRepository(mapOf(4L to workout(exercise)))
        val catalog = FakeExerciseCatalogRepository()
        catalog.allExercises.value = listOf(CatalogExercise("barbell-bench-press", "Жим", exercise.type), replacement)
        val workoutId = requireNotNull(workouts.startWorkout(4))
        val viewModel = ActiveWorkoutState(
            workouts, -1, { testScheduler.currentTime }, NoTimerSignals, catalog, backgroundScope, NoopWorkoutTimerStore,
        )
        viewModel.open(workoutId)
        runCurrent()
        assertEquals(0, catalog.allExercises.subscriptionCount.value)
        viewModel.openReplacement()
        assertNull(viewModel.uiState.value.replacement?.options)
        runCurrent()
        assertEquals(listOf(replacement), viewModel.uiState.value.replacement?.options?.similar)
        assertEquals(1, catalog.allExercises.subscriptionCount.value)
        viewModel.closeReplacement()
        viewModel.openReplacement()
        assertNull(viewModel.uiState.value.replacement?.options)
        viewModel.closeReplacement()
        runCurrent()
        assertNull(viewModel.uiState.value.replacement)
        assertEquals(0, catalog.allExercises.subscriptionCount.value)

        viewModel.changeDraft(exercise, 1, SetInputField.Weight, "62,5")
        viewModel.changeDraft(exercise, 1, SetInputField.Measure, "8")
        val input = requireNotNull(viewModel.uiState.value.rows(exercise.id).inputs[1])
        assertEquals("62,5", input.weight)
        assertEquals("8", input.measure)
        viewModel.logSet(workoutId, exercise, 1)
        runCurrent()
        val stored = requireNotNull(viewModel.uiState.value.session).exercises.single().sets.single()
        assertEquals(SetResult.WeightAndReps(62.5, 8), stored.result)
        assertNull(viewModel.uiState.value.rows(exercise.id).inputs[1])
        assertEquals(1, viewModel.uiState.value.savedPosition)
    }

    @Test
    fun workoutRejectsInvalidCorrectionsAndKeepsTheLoggedSet() = runTest {
        val set = LoggedSet(1, SetResult.WeightAndReps(60.5, 6))
        val exercise = WorkoutExercise(8, "Жим", listOf(set))
        val workouts = FakeWorkoutsRepository(mapOf(4L to workout(exercise)))
        val workoutId = requireNotNull(workouts.startWorkout(4))
        val active = ActiveWorkoutState(
            workouts, -1, { testScheduler.currentTime }, NoTimerSignals, FakeExerciseCatalogRepository(), backgroundScope, NoopWorkoutTimerStore,
        )
        active.open(workoutId)
        runCurrent()

        active.openSetEditor(exercise, 1, set)
        active.changeSetEdit(SetInputField.Weight, "62.50001")
        active.changeSetEdit(SetInputField.Measure, "0")
        active.saveSetEdit(workoutId)
        runCurrent()

        assertEquals(
            SetInputErrors("Введи вес, например 62,5", "Целое число от 1"),
            active.uiState.value.editingSet?.input?.errors,
        )
        assertEquals(listOf(set), active.uiState.value.session?.exercises?.single()?.sets)
    }

    @Test
    fun historyLoadsOnlySelectedDetailsAndValidatesBeforeUpdatingTheSet() = runTest {
        val set = LoggedSet(1, SetResult.Reps(8), id = 12)
        val exercise = WorkoutExercise(8, "Pull-up", listOf(set), type = ExerciseType.REPS)
        val workouts = FakeWorkoutsRepository(mapOf(4L to workout(exercise)))
        val firstId = requireNotNull(workouts.startWorkout(4))
        workouts.finishWorkout(firstId)
        val secondId = requireNotNull(workouts.startWorkout(4))
        workouts.finishWorkout(secondId)
        withViewModel({ HistoryViewModel(workouts) }) { history ->
            backgroundScope.launch { history.uiState.collect() }
            runCurrent()
            assertEquals(listOf(firstId, secondId), history.uiState.value.history?.map { it.id })
        }
        var historyObserved = false
        val detailsRepository = object : WorkoutsRepository by workouts {
            override val history = workouts.history.onStart { historyObserved = true }
        }
        withViewModel({ WorkoutDetailsViewModel(secondId, detailsRepository) }) { details ->
            assertFalse(historyObserved)
            details.uiState.test {
                assertNull(awaitItem().details)
                assertEquals(secondId, awaitItem().details?.workout?.summary?.id)

                details.saveSet(set, SetInput(ExerciseType.REPS))
                runCurrent()
                assertEquals("Целое число от 1", details.uiState.value.setErrors?.measure)
                details.saveSet(set, SetInput(ExerciseType.REPS, measure = "10"))
                runCurrent()
                val updated = details.uiState.value.details?.workout?.exercises?.single()?.sets?.single()
                assertEquals(SetResult.Reps(10), updated?.result)
                assertNull(details.uiState.value.setErrors)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun replacementDropsThePreviousExercisesDraftWhenRoomPublishesFirst() = runTest {
        val exercise = WorkoutExercise(8, "Жим", emptyList(), catalogExerciseId = "barbell-bench-press")
        val replacement = CatalogExercise("dumbbell-bench-press", "Жим гантелей", exercise.type)
        val catalog = FakeExerciseCatalogRepository()
        val workouts = FakeWorkoutsRepository(mapOf(4L to workout(exercise)))
        val complete = CompletableDeferred<Unit>()
        val repository = object : WorkoutsRepository by workouts {
            override suspend fun replaceExercise(
                sessionId: Long,
                exerciseId: Long,
                sourceCatalogId: String,
                replacement: CatalogExercise,
            ): Boolean {
                val replaced = workouts.replaceExercise(sessionId, exerciseId, sourceCatalogId, replacement)
                complete.await()
                return replaced
            }
        }
        val workoutId = requireNotNull(workouts.startWorkout(4))
        val viewModel = ActiveWorkoutState(
            repository, -1, { testScheduler.currentTime }, NoTimerSignals, catalog, backgroundScope, NoopWorkoutTimerStore,
        )
        viewModel.open(workoutId)
        runCurrent()
        viewModel.changeDraft(exercise, 1, SetInputField.Weight, "62,5")
        viewModel.openReplacement()
        viewModel.replaceExercise(replacement)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.saving)
        assertEquals(replacement.id, state.session?.exercises?.single()?.catalogExerciseId)
        assertNull(state.replacement)
        assertTrue(state.rows(exercise.id).inputs.isEmpty())
        complete.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun settingsKeepsCheckResultWhileWarningAndDownloading() = runTest {
        val installed = InstalledApp("1", 1)
        val update = FittyUpdate("2", 2, "https://example.test/fitty.apk")
        val available = UpdateCheckResult.Available(update)
        val apk = File("fitty-test.apk")
        val service = object : UpdateService {
            override suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult = available
            override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = Unit
            override suspend fun download(update: FittyUpdate, installedVersionCode: Long, onProgress: (Int) -> Unit) =
                UpdateDownloadResult.Downloaded(apk)
        }
        val settings = UpdateCoordinator(
            installed, service, NoopSessionManager, NoopAttemptStore, FakeWorkoutsRepository(), backgroundScope,
        )
        settings.checkForUpdate()
        assertEquals(UpdateCheckState.Checking, settings.uiState.value.updateCheck)
        runCurrent()
        settings.startUpdate(hasUnsavedInput = true)
        runCurrent()
        assertEquals(UpdateInstallState.Warning, settings.uiState.value.updateInstall)
        settings.confirmDownload()
        assertEquals(UpdateInstallState.Downloading(), settings.uiState.value.updateInstall)
        runCurrent()
        val state = settings.uiState.value
        assertEquals(installed, state.installedApp)
        assertEquals(UpdateCheckState.Checked(available), state.updateCheck)
        assertEquals(UpdateInstallState.Ready(apk, 2), state.updateInstall)
    }

    private suspend fun <T : ViewModel> TestScope.withViewModel(
        create: () -> T,
        verify: suspend (T) -> Unit,
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = create()
            try {
                verify(viewModel)
            } finally {
                viewModel.viewModelScope.cancel()
                runCurrent()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun workout(vararg exercises: WorkoutExercise, dayName: String = "A") =
        WorkoutSession(WorkoutSummary(0, "Strength", dayName, null), exercises.toList())

    private object NoTimerSignals : SetTimerSignalScheduler {
        override fun schedule(token: String, workoutId: Long, deadlineMillis: Long) = true
        override fun cancel(token: String) = Unit
        override fun cancelWorkout(workoutId: Long) = Unit
    }

    private object NoopSessionManager : UpdateSessionManager {
        override fun canRequestInstall() = false
        override suspend fun commitInstall(apk: File, expectedVersionCode: Long) = null
        override suspend fun abandonSession(sessionId: Int) = Unit
        override fun hasSession(sessionId: Int) = false
        override fun hasConfirmation(sessionId: Int, requestId: Long) = false
    }

    private object NoopAttemptStore : UpdateAttemptStore {
        override fun read() = null
        override fun write(attempt: UpdateAttempt) = Unit
        override fun clear() = Unit
    }
}
