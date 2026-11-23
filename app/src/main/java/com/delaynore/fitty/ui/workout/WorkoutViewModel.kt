package com.delaynore.fitty.ui.workout

import androidx.lifecycle.ViewModel

internal class WorkoutViewModel(val activeWorkout: ActiveWorkoutState) : ViewModel() {
    val uiState = activeWorkout.uiState
}
