package com.delaynore.fitty.domain

internal object NoopWorkoutTimerStore : WorkoutTimerStore {
    override fun read() = WorkoutTimersSnapshot()
    override fun save(snapshot: WorkoutTimersSnapshot) = Unit
}
