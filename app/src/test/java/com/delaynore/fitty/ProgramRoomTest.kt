package com.delaynore.fitty

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.data.exercises.ExerciseGoal
import com.delaynore.fitty.data.exercises.ExerciseType
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import com.delaynore.fitty.data.workouts.LoggedSet
import com.delaynore.fitty.data.workouts.RoomWorkoutsRepository
import com.delaynore.fitty.data.workouts.SetResult
import com.delaynore.fitty.ui.programs.ProgramEditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "program-room-test.db"
    private lateinit var database: FittyDatabase
    private lateinit var programs: RoomProgramsRepository
    private lateinit var workouts: RoomWorkoutsRepository
    private lateinit var editor: ProgramEditorViewModel

    @Before
    fun openDatabase() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        reopen()
    }

    @After
    fun deleteDatabase() {
        editor.viewModelScope.cancel()
        Dispatchers.resetMain()
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun savesTheEditedGoalAndOrderAndUsesThemInTheWorkout() = withRoom {
        val day = TrainingDay(0, "Upper body", listOf(
            TrainingExercise(0, "Bench press", null, null),
            TrainingExercise(0, "Pulldown", 3, ExerciseGoal.Repetitions(8)),
        ))
        programs.saveProgram(TrainingProgram(0, "Warmup", listOf(day.copy(name = "Warmup"))))
        val warmup = TrainingDay(0, "Warmup", listOf(TrainingExercise(0, "Walk", null, null)))
        programs.saveProgram(TrainingProgram(0, "Strength", listOf(warmup, day)))
        assertEquals("Warmup", programs.homeProgram.first()?.name)
        val strengthId = programs.allPrograms.first().single { it.name == "Strength" }.id
        programs.selectProgram(strengthId)
        val saved = requireNotNull(programs.homeProgram.first())
        assertEquals(strengthId, saved.id)
        val selectedDay = saved.days.single { it.name == "Upper body" }
        val dayId = selectedDay.id
        val pulldownId = selectedDay.exercises.last().id

        editor.openEditor(saved)
        editor.openDay(dayId)
        editor.changeExercise(pulldownId) { it.copy(target = "10") }
        editor.moveExercise(pulldownId, 0)
        save(editor)

        reopen()
        val reloaded = requireNotNull(programs.observeProgram(saved.id).first())
        editor.openEditor(reloaded)
        editor.openDay(dayId)
        val reopenedDay = requireNotNull(editor.uiState.value.editingDay)
        assertEquals(listOf("Pulldown", "Bench press"), reopenedDay.exercises.map { it.name })
        assertEquals("3", reopenedDay.exercises.first().sets)
        assertEquals(ExerciseGoal.Repetitions(10), reopenedDay.exercises.first().goal)

        val workoutId = requireNotNull(workouts.startWorkout(dayId))
        val workout = requireNotNull(workouts.observeWorkout(workoutId).first())
        assertEquals("Upper body", workout.summary.dayName)
        assertEquals(listOf("Pulldown", "Bench press"), workout.exercises.map { it.name })
        assertEquals(3, workout.exercises.first().recommendedSets)
        assertEquals(ExerciseGoal.Repetitions(10), workout.exercises.first().goal)
    }

    @Test
    fun linkingALegacyExerciseToACatalogCardOnlyChangesFutureSessions() = withRoom {
        val legacy = TrainingExercise(0, "Press", 3, ExerciseGoal.Repetitions(8))
        val fly = TrainingExercise(0, "Fly", null, null)
        val card = CatalogExercise("dumbbell-bench-press", "Dumbbell bench press", ExerciseType.WEIGHT_AND_REPS)
        programs.saveProgram(TrainingProgram(0, "Strength", listOf(TrainingDay(0, "Chest", listOf(legacy, fly)))))
        val program = requireNotNull(programs.homeProgram.first())
        val day = program.days.single()
        val recordedId = requireNotNull(workouts.startWorkout(day.id))
        val press = requireNotNull(workouts.observeWorkout(recordedId).first()).exercises.first()
        val loggedResult = SetResult.WeightAndReps(60.0, 6)
        assertTrue(workouts.logSet(recordedId, press, LoggedSet(1, loggedResult)))
        assertTrue(workouts.logSet(recordedId, press, LoggedSet(2, loggedResult)))
        assertTrue(workouts.finishWorkout(recordedId))
        val inProgressId = requireNotNull(workouts.startWorkout(day.id))
        val inProgress = requireNotNull(workouts.observeWorkout(inProgressId).first())

        val exerciseId = day.exercises.first().id
        editor.openEditor(program)
        editor.openDay(day.id)
        editor.selectExercise(card, exerciseId)
        assertEquals(ExerciseGoal.Repetitions(8), editor.uiState.value.editingDay?.exercises?.first()?.goal)
        editor.changeExercise(exerciseId) { it.copy(sets = "4", target = "5") }
        editor.deleteExercise(day.exercises.last().id)
        save(editor)

        reopen()
        assertEquals(inProgress, workouts.observeWorkout(inProgressId).first())
        assertTrue(workouts.cancelWorkout(inProgressId))
        val newId = requireNotNull(workouts.startWorkout(day.id))
        assertNotEquals(recordedId, newId)
        val newExercise = requireNotNull(workouts.observeWorkout(newId).first()).exercises.single()
        assertEquals(card.id, newExercise.catalogExerciseId)
        assertEquals(card.name, newExercise.name)
        assertEquals(4, newExercise.recommendedSets)
        assertEquals(ExerciseGoal.Repetitions(5), newExercise.goal)

        val historyId = workouts.history.first().single().id
        assertEquals(recordedId, historyId)
        val history = requireNotNull(workouts.observeWorkout(historyId).first())
        assertEquals(listOf("Press", "Fly"), history.exercises.map { it.name })
        assertEquals(listOf(loggedResult, loggedResult), history.exercises.first().sets.map { it.result })
        assertEquals(ExerciseGoal.Repetitions(8), history.exercises.first().goal)
    }

    private fun withRoom(verify: suspend () -> Unit) = runBlocking {
        withTimeout(5_000) { verify() }
    }

    private fun reopen() {
        if (::editor.isInitialized) editor.viewModelScope.cancel()
        if (::database.isInitialized) database.close()
        database = Room.databaseBuilder(context, FittyDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        programs = RoomProgramsRepository(database)
        workouts = RoomWorkoutsRepository(database)
        editor = ProgramEditorViewModel(programs)
    }

    private suspend fun save(editor: ProgramEditorViewModel) {
        val saved = CompletableDeferred<Unit>()
        editor.saveProgram({ error("The day is valid") }, { saved.complete(Unit) })
        saved.await()
    }
}
