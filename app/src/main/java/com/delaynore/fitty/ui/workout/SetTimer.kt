package com.delaynore.fitty.ui.workout

import com.delaynore.fitty.domain.SavedSetTimer

internal typealias SetTimer = com.delaynore.fitty.domain.SetTimer
internal typealias SetTimerSignal = com.delaynore.fitty.domain.SetTimerSignal
internal typealias SetTimerSignalScheduler = com.delaynore.fitty.domain.SetTimerSignalScheduler

internal fun SetTimer.saved(bootCount: Int) = SavedSetTimer(
    token, workoutId, exerciseId, catalogExerciseId, position, startedAtMillis, goalAtMillis, stoppedAtMillis, bootCount,
)

internal fun restoreSetTimer(state: SavedSetTimer?, workoutId: Long, bootCount: Int, now: Long): SetTimer? {
    if (state == null || bootCount < 0 || state.bootCount != bootCount ||
        state.workoutId != workoutId || state.startedAtMillis > now
    ) return null
    return SetTimer(
        token = state.token,
        workoutId = workoutId,
        exerciseId = state.exerciseId,
        catalogExerciseId = state.catalogExerciseId,
        position = state.position,
        startedAtMillis = state.startedAtMillis,
        goalAtMillis = state.goalAtMillis,
        stoppedAtMillis = state.stoppedAtMillis,
    )
}
