package com.delaynore.fitty.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.history.HistoryScreen
import com.delaynore.fitty.ui.history.HistoryViewModel
import com.delaynore.fitty.ui.history.WorkoutDetailsScreen
import com.delaynore.fitty.ui.home.HomeScreen
import com.delaynore.fitty.ui.home.HomeViewModel
import com.delaynore.fitty.ui.programs.ProgramEditorScreen
import com.delaynore.fitty.ui.programs.ProgramEditorViewModel
import com.delaynore.fitty.ui.programs.TrainingDayEditorScreen
import com.delaynore.fitty.ui.workout.WorkoutScreen
import com.delaynore.fitty.ui.workout.WorkoutViewModel

private enum class Destination { Home, ProgramEditor, DayEditor, Workout, History, WorkoutDetails }

@Composable
internal fun FittyNavigation(programs: ProgramsRepository, workouts: WorkoutsRepository) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var workoutId by rememberSaveable { mutableLongStateOf(0L) }
    val programEditor = viewModel { ProgramEditorViewModel(programs) }
    val history = viewModel { HistoryViewModel(workouts) }

    val back = {
        destination = when (destination) {
            Destination.DayEditor -> Destination.ProgramEditor
            Destination.WorkoutDetails -> Destination.History
            else -> Destination.Home
        }
    }
    BackHandler(enabled = destination != Destination.Home, onBack = back)

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (destination in listOf(Destination.Home, Destination.History, Destination.WorkoutDetails)) {
                TabBar(
                    homeSelected = destination == Destination.Home,
                    onHome = { destination = Destination.Home },
                    onHistory = { destination = Destination.History },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                Destination.Home -> HomeScreen(
                    viewModel = viewModel { HomeViewModel(programs, workouts) },
                    onCreateProgram = {
                        programEditor.reset()
                        destination = Destination.ProgramEditor
                    },
                    onWorkout = { id ->
                        workoutId = id
                        destination = Destination.Workout
                    },
                )
                Destination.ProgramEditor -> ProgramEditorScreen(
                    viewModel = programEditor,
                    onBack = back,
                    onEditDay = { destination = Destination.DayEditor },
                    onSaved = { destination = Destination.Home },
                )
                Destination.DayEditor -> TrainingDayEditorScreen(programEditor, back)
                Destination.Workout -> WorkoutScreen(
                    id = workoutId,
                    viewModel = viewModel { WorkoutViewModel(workouts) },
                    onBack = back,
                    onFinished = { destination = Destination.WorkoutDetails },
                )
                Destination.History -> HistoryScreen(history) { id ->
                    workoutId = id
                    destination = Destination.WorkoutDetails
                }
                Destination.WorkoutDetails -> WorkoutDetailsScreen(workoutId, history, back)
            }
        }
    }
}

@Composable
private fun TabBar(homeSelected: Boolean, onHome: () -> Unit, onHistory: () -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        NavigationBarItem(
            selected = homeSelected,
            onClick = onHome,
            label = { Text("Главная") },
            icon = { FittyGlyph(FittyIcon.Home) },
        )
        NavigationBarItem(
            selected = !homeSelected,
            onClick = onHistory,
            label = { Text("История") },
            icon = { FittyGlyph(FittyIcon.History) },
        )
    }
}
