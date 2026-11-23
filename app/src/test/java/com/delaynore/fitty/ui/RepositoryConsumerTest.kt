package com.delaynore.fitty.ui

import app.cash.turbine.test
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.exercises.FakeExerciseCatalogRepository
import com.delaynore.fitty.data.programs.FakeProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.FakeWorkoutsRepository
import com.delaynore.fitty.data.workouts.WorkoutSession
import com.delaynore.fitty.data.workouts.WorkoutSummary
import com.delaynore.fitty.ui.exercises.ExerciseEditorViewModel
import com.delaynore.fitty.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryConsumerTest {
    @Test
    fun homeStartsTheSelectedDayAndObservesCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val programs = FakeProgramsRepository()
        val days = listOf(TrainingDay(4, "A", emptyList()), TrainingDay(5, "B", emptyList()))
        val program = TrainingProgram(1, "Strength", days)
        programs.saveProgram(program)
        val workouts = FakeWorkoutsRepository(days.associate { day ->
            day.id to WorkoutSession(WorkoutSummary(0, program.name, day.name, null), emptyList())
        })
        var cancelledId: Long? = null
        val home = HomeViewModel(programs, workouts) { cancelledId = it }
        try {
            home.home.test {
                assertNull(awaitItem())
                assertEquals(program, awaitItem()?.program)
                home.selectDay(5)
                var startedId: Long? = null
                home.startWorkout { startedId = it }
                val active = requireNotNull(awaitItem()?.inProgressWorkout)
                assertEquals("B", active.dayName)
                assertEquals(active.id, startedId)
                home.cancelWorkout(active.id) {}
                assertNull(awaitItem()?.inProgressWorkout)
                assertEquals(startedId, cancelledId)
            }
        } finally {
            home.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exerciseEditorSavesTheSelectedTypeInTheCatalog() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val catalog = FakeExerciseCatalogRepository()
        val editor = ExerciseEditorViewModel(catalog)
        try {
            editor.open("Планка")
            editor.selectType(ExerciseType.DURATION)
            editor.save()
            runCurrent()
            val saved = catalog.allExercises.value.single()
            assertEquals("Планка", saved.name)
            assertEquals(ExerciseType.DURATION, saved.type)
            assertEquals(saved, editor.savedExercise)
        } finally {
            editor.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
