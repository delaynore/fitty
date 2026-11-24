package com.delaynore.fitty.data.workouts

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.programs.TrainingDay
import com.delaynore.fitty.data.programs.TrainingExercise
import com.delaynore.fitty.data.programs.TrainingProgram
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InProgressWorkoutRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "in-progress-test.db"
    private lateinit var database: FittyDatabase
    private lateinit var workouts: WorkoutsRepository
    private var dayId = 0L

    @Before
    fun createTrainingDay() = runBlocking {
        reopen()
        val programs = RoomProgramsRepository(database)
        programs.saveProgram(TrainingProgram(0, "Силовая", listOf(
            TrainingDay(0, "День А", listOf(TrainingExercise(0, "Жим штанги лёжа", null, null))),
        )))
        dayId = requireNotNull(programs.homeProgram.first()).days.single().id
    }

    @After
    fun deleteDatabase() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun recordedAndCorrectedSetSurvivesReopening() = runBlocking {
        val id = requireNotNull(workouts.startWorkout(dayId))
        assertFalse(workouts.finishWorkout(id))
        val recorded = LoggedSet(1, SetResult.WeightAndReps(60.5, 6))
        assertTrue(workouts.logSet(id, exercise(id), recorded))

        reopen()
        val stored = exercise(id).sets.single()
        assertEquals(recorded.position, stored.position)
        assertEquals(recorded.result, stored.result)
        val corrected = stored.copy(result = SetResult.WeightAndReps(62.5, 7))
        assertTrue(workouts.updateInProgressSet(id, exercise(id).id, corrected))

        reopen()
        assertEquals(listOf(corrected), exercise(id).sets)
        assertEquals(id, workouts.inProgressWorkout.first()?.id)
    }

    @Test
    fun removedSetsStayGoneAfterReopeningAndCanBeRestoredIndependently() = runBlocking {
        val id = requireNotNull(workouts.startWorkout(dayId))
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(1, SetResult.WeightAndReps(60.0, 6))))
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(2, SetResult.WeightAndReps(65.0, 5))))
        val original = exercise(id)
        val second = requireNotNull(workouts.removeSetPosition(id, original, 2, isRecorded = true))
        val first = requireNotNull(workouts.removeSetPosition(id, original, 1, isRecorded = true))

        reopen()
        val removed = exercise(id)
        assertEquals(emptyList<LoggedSet>(), removed.sets)
        assertEquals(setOf(1, 2), removed.removedPositions)
        assertFalse(workouts.finishWorkout(id))
        assertTrue(workouts.restoreSetPosition(id, removed.id, 1, first.set))
        assertEquals(listOf(original.sets[0]), exercise(id).sets)
        assertEquals(setOf(2), exercise(id).removedPositions)
        assertTrue(workouts.restoreSetPosition(id, removed.id, 2, second.set))

        reopen()
        assertEquals(original.sets, exercise(id).sets)
        assertEquals(emptySet<Int>(), exercise(id).removedPositions)
    }

    @Test
    fun deletingFirstSetKeepsSecondAndAllowsAnotherSetAfterReopening() = runBlocking {
        val id = requireNotNull(workouts.startWorkout(dayId))
        val first = SetResult.WeightAndReps(60.0, 6)
        val second = SetResult.WeightAndReps(65.0, 5)
        val third = SetResult.WeightAndReps(70.0, 4)
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(1, first)))
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(2, second)))
        requireNotNull(workouts.removeSetPosition(id, exercise(id), 1, isRecorded = true))

        reopen()
        assertEquals(second, exercise(id).sets.single().result)
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(3, third)))
        assertEquals(listOf(2 to second, 3 to third), exercise(id).sets.map { it.position to it.result })
    }

    @Test
    fun cancellingDiscardsWorkoutAndAllowsStartingSameTrainingDay() = runBlocking {
        val id = requireNotNull(workouts.startWorkout(dayId))
        assertTrue(workouts.logSet(id, exercise(id), LoggedSet(1, SetResult.WeightAndReps(60.0, 6))))

        reopen()
        assertEquals(InProgressWorkout(id, "День А", 1), workouts.inProgressWorkout.first())
        assertTrue(workouts.cancelWorkout(id))

        reopen()
        assertNull(workouts.inProgressWorkout.first())
        assertNull(workouts.observeWorkout(id).first())
        assertEquals(emptyList<WorkoutSummary>(), workouts.history.first())
        val nextId = requireNotNull(workouts.startWorkout(dayId))
        assertNotEquals(id, nextId)
        assertEquals(InProgressWorkout(nextId, "День А", 0), workouts.inProgressWorkout.first())
        assertEquals(emptyList<LoggedSet>(), exercise(nextId).sets)
    }

    private suspend fun exercise(id: Long): WorkoutExercise =
        requireNotNull(workouts.observeWorkout(id).first()).exercises.single()

    private fun reopen() {
        if (::database.isInitialized) database.close()
        database = Room.databaseBuilder(context, FittyDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        workouts = RoomWorkoutsRepository(database)
    }
}
