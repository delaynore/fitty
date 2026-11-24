package com.delaynore.fitty.ui.history

import androidx.lifecycle.ViewModelStore
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.data.workouts.WorkoutExercise
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.ui.SetInputErrors
import com.delaynore.fitty.ui.toInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class HistoryViewModelTest {
    @Test
    fun invalidEditsKeepTheOriginalResultUntilBothFieldsAreCorrected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var collection: Job? = null
        try {
            val (repository, id) = completedWorkout()
            val viewModel = WorkoutDetailsViewModel(id, repository)
            store.put("history", viewModel)
            collection = backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()
            val set = viewModel.uiState.value.details?.workout?.exercises?.single()?.sets?.single()
                ?: error("Expected the completed set")
            val input = set.result.toInput()
            val invalid = input.copy(weight = "62,50001", measure = "0")
            viewModel.openSetEditor(set.id)
            viewModel.saveSet(set, invalid)
            runCurrent()
            assertEquals(set.id, viewModel.uiState.value.editingSetId)
            assertEquals(SetInputErrors("Введи вес, например 62,5", "Целое число от 1"), viewModel.uiState.value.setErrors)
            assertEquals(set, viewModel.uiState.value.details?.workout?.exercises?.single()?.sets?.single())

            val corrected = input.copy(weight = "62,5", measure = "7")
            viewModel.changeSetInput(corrected)
            runCurrent()
            assertEquals(SetInputErrors(), viewModel.uiState.value.setErrors)
            viewModel.saveSet(set, corrected)
            runCurrent()
            assertNull(viewModel.uiState.value.editingSetId)
            assertEquals(
                set.copy(result = SetResult.WeightAndReps(62.5, 7)),
                viewModel.uiState.value.details?.workout?.exercises?.single()?.sets?.single(),
            )
        } finally {
            collection?.cancel()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}

internal suspend fun completedWorkout(): Pair<FakeWorkoutsRepository, Long> {
    val session = WorkoutSession(
        WorkoutSummary(0, "Strength", "Day A", null),
        listOf(WorkoutExercise(1, "Bench press", emptyList())),
    )
    val repository = FakeWorkoutsRepository(mapOf(1L to session))
    val id = requireNotNull(repository.startWorkout(1L))
    check(repository.logSet(id, session.exercises.single(), LoggedSet(1, SetResult.WeightAndReps(60.0, 6))))
    check(repository.finishWorkout(id))
    return repository to id
}
