package com.delaynore.fitty.platform.workout

import android.content.Context
import com.delaynore.fitty.domain.SavedRestTimer
import com.delaynore.fitty.domain.SavedSetTimer
import com.delaynore.fitty.domain.WorkoutTimerStore
import com.delaynore.fitty.domain.WorkoutTimersSnapshot

internal class SharedPreferencesWorkoutTimerStore(context: Context, databaseName: String) : WorkoutTimerStore {
    private val preferences = context.getSharedPreferences(fileName(databaseName), Context.MODE_PRIVATE)

    override fun read() = WorkoutTimersSnapshot(readRestTimer(), readSetTimer())

    override fun save(snapshot: WorkoutTimersSnapshot) {
        preferences.edit().clear().apply {
            snapshot.restTimer?.let {
                putLong("rest.workoutId", it.workoutId)
                putLong("rest.deadline", it.deadlineMillis)
                putInt("rest.boot", it.bootCount)
            }
            snapshot.setTimer?.let {
                putString("set.token", it.token)
                putLong("set.workoutId", it.workoutId)
                putLong("set.exerciseId", it.exerciseId)
                putString("set.catalogExerciseId", it.catalogExerciseId)
                putInt("set.position", it.position)
                putLong("set.startedAt", it.startedAtMillis)
                it.goalAtMillis?.let { value -> putLong("set.goalAt", value) }
                it.stoppedAtMillis?.let { value -> putLong("set.stoppedAt", value) }
                putInt("set.boot", it.bootCount)
            }
        }.apply()
    }

    private fun readRestTimer(): SavedRestTimer? = runCatching {
        if (!preferences.contains("rest.workoutId") || !preferences.contains("rest.deadline") ||
            !preferences.contains("rest.boot")
        ) return null
        SavedRestTimer(
            preferences.getLong("rest.workoutId", 0),
            preferences.getLong("rest.deadline", 0),
            preferences.getInt("rest.boot", -1),
        )
    }.getOrNull()

    private fun readSetTimer(): SavedSetTimer? = runCatching {
        if (!preferences.contains("set.token") || !preferences.contains("set.workoutId") ||
            !preferences.contains("set.exerciseId") || !preferences.contains("set.position") ||
            !preferences.contains("set.startedAt") || !preferences.contains("set.boot")
        ) return null
        SavedSetTimer(
            preferences.getString("set.token", null) ?: return null,
            preferences.getLong("set.workoutId", 0),
            preferences.getLong("set.exerciseId", 0),
            preferences.getString("set.catalogExerciseId", null),
            preferences.getInt("set.position", 0),
            preferences.getLong("set.startedAt", 0),
            preferences.getLong("set.goalAt", -1).takeIf { preferences.contains("set.goalAt") },
            preferences.getLong("set.stoppedAt", -1).takeIf { preferences.contains("set.stoppedAt") },
            preferences.getInt("set.boot", -1),
        )
    }.getOrNull()

    companion object {
        private fun fileName(databaseName: String) = "$databaseName.timers"
        fun clear(context: Context, databaseName: String) = context.deleteSharedPreferences(fileName(databaseName))
    }
}
