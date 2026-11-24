package com.delaynore.fitty.ui.workout

import android.os.Bundle
import androidx.core.os.bundleOf

internal typealias SetTimer = com.delaynore.fitty.domain.SetTimer

internal fun SetTimer.saved(bootCount: Int): Bundle = bundleOf(
    "boot" to bootCount,
    "token" to token,
    "workoutId" to workoutId,
    "exerciseId" to exerciseId,
    "catalogExerciseId" to catalogExerciseId,
    "position" to position,
    "startedAt" to startedAtMillis,
    "goalAt" to (goalAtMillis ?: -1L),
    "stoppedAt" to (stoppedAtMillis ?: -1L),
)

internal fun restoreSetTimer(state: Bundle?, workoutId: Long, bootCount: Int, now: Long): SetTimer? {
    if (state == null || bootCount < 0 || state.getInt("boot", -1) != bootCount ||
        state.getLong("workoutId") != workoutId || state.getLong("startedAt") > now
    ) return null
    return SetTimer(
        token = state.getString("token") ?: return null,
        workoutId = workoutId,
        exerciseId = state.getLong("exerciseId"),
        catalogExerciseId = state.getString("catalogExerciseId"),
        position = state.getInt("position"),
        startedAtMillis = state.getLong("startedAt"),
        goalAtMillis = state.getLong("goalAt", -1L).takeIf { it >= 0 },
        stoppedAtMillis = state.getLong("stoppedAt", -1L).takeIf { it >= 0 },
    )
}
