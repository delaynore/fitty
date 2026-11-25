package com.delaynore.fitty.platform.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.domain.SavedRestTimer
import com.delaynore.fitty.domain.SavedSetTimer
import com.delaynore.fitty.domain.WorkoutTimersSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesWorkoutTimerStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun snapshotRoundTripsAndDatabaseNamesAreIsolated() {
        val snapshot = WorkoutTimersSnapshot(
            SavedRestTimer(11, 12_000, 3),
            SavedSetTimer("token", 11, 22, "plank", 2, 1_000, 5_000, 2_000, 3),
        )
        SharedPreferencesWorkoutTimerStore(context, "first.db").save(snapshot)

        assertEquals(snapshot, SharedPreferencesWorkoutTimerStore(context, "first.db").read())
        assertEquals(WorkoutTimersSnapshot(), SharedPreferencesWorkoutTimerStore(context, "second.db").read())
    }

    @Test
    fun malformedTypedValuesAreIgnored() {
        context.getSharedPreferences("broken.db.timers", Context.MODE_PRIVATE).edit()
            .putString("rest.workoutId", "not a long")
            .putString("set.token", "token")
            .putString("set.workoutId", "not a long")
            .apply()

        assertEquals(WorkoutTimersSnapshot(), SharedPreferencesWorkoutTimerStore(context, "broken.db").read())
    }

    @Test
    fun clearRemovesPersistedTimersForDatabase() {
        val snapshot = WorkoutTimersSnapshot(
            SavedRestTimer(11, 12_000, 3),
            SavedSetTimer("token", 11, 22, "plank", 2, 1_000, 5_000, 2_000, 3),
        )
        val store = SharedPreferencesWorkoutTimerStore(context, "clear.db")
        store.save(snapshot)
        assertEquals(snapshot, store.read())

        SharedPreferencesWorkoutTimerStore.clear(context, "clear.db")

        assertEquals(WorkoutTimersSnapshot(), SharedPreferencesWorkoutTimerStore(context, "clear.db").read())
    }
}
