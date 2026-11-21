package com.delaynore.fitty.navigation

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.history.HistoryScreen
import com.delaynore.fitty.ui.history.HistoryViewModel
import com.delaynore.fitty.ui.history.WorkoutDetailsScreen
import com.delaynore.fitty.ui.home.HomeScreen
import com.delaynore.fitty.ui.home.HomeViewModel
import com.delaynore.fitty.ui.programs.ProgramDetailsScreen
import com.delaynore.fitty.ui.programs.ProgramEditorScreen
import com.delaynore.fitty.ui.programs.ProgramEditorViewModel
import com.delaynore.fitty.ui.programs.ProgramsScreen
import com.delaynore.fitty.ui.programs.TrainingDayEditorScreen
import com.delaynore.fitty.ui.settings.InstalledApp
import com.delaynore.fitty.ui.settings.SettingsScreen
import com.delaynore.fitty.ui.settings.UpdateInstallerEffect
import com.delaynore.fitty.ui.settings.SettingsViewModel
import com.delaynore.fitty.ui.workout.WorkoutScreen
import com.delaynore.fitty.ui.workout.WorkoutViewModel

private enum class Destination { Home, Programs, ProgramDetails, ProgramEditor, DayEditor, Workout, History, WorkoutDetails, Settings }

private data class BottomTab(val destination: Destination, val label: String, val icon: FittyIcon)

private val bottomTabs = listOf(
    BottomTab(Destination.Home, "Главная", FittyIcon.Home),
    BottomTab(Destination.History, "История", FittyIcon.History),
    BottomTab(Destination.Settings, "Настройки", FittyIcon.Settings),
)

@Composable
internal fun FittyNavigation(
    programs: ProgramsRepository,
    workouts: WorkoutsRepository,
    restTimerClock: () -> Long,
    installedApp: InstalledApp,
    updateService: UpdateService,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var workoutId by rememberSaveable { mutableLongStateOf(0L) }
    var programId by rememberSaveable { mutableLongStateOf(0L) }
    var settingsOrigin by rememberSaveable { mutableStateOf<Destination?>(null) }
    var editorOrigin by rememberSaveable { mutableStateOf(Destination.Home) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val home = viewModel { HomeViewModel(programs, workouts) }
    val programEditor = viewModel { ProgramEditorViewModel(programs) }
    val history = viewModel { HistoryViewModel(workouts) }
    val settings = viewModel { SettingsViewModel(installedApp, updateService, workouts, createSavedStateHandle()) }
    UpdateInstallerEffect(settings) { destination == Destination.Settings }
    val context = LocalContext.current
    val workout = viewModel {
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        WorkoutViewModel(workouts, createSavedStateHandle(), bootCount, restTimerClock)
    }

    val closeEditor = {
        confirmDiscard = false
        programEditor.closeEditor()
        destination = editorOrigin
    }
    val editorSettings = {
        settingsOrigin = destination
        destination = Destination.Settings
    }
    val settingsFromEditor = settingsOrigin != null && programEditor.editing
    val back = {
        when (destination) {
            Destination.DayEditor -> destination = Destination.ProgramEditor
            Destination.ProgramEditor -> if (!programEditor.saving) {
                if (programEditor.hasChanges) confirmDiscard = true else closeEditor()
            }
            Destination.ProgramDetails -> destination = Destination.Programs
            Destination.WorkoutDetails -> destination = Destination.History
            Destination.Settings -> {
                destination = settingsOrigin?.takeIf { programEditor.editing } ?: Destination.Home
                settingsOrigin = null
            }
            else -> destination = Destination.Home
        }
    }
    val createProgram = {
        editorOrigin = destination
        programEditor.openEditor()
        destination = Destination.ProgramEditor
    }
    LaunchedEffect(destination) {
        if ((destination == Destination.ProgramEditor || destination == Destination.DayEditor) && !programEditor.editing) {
            closeEditor()
        }
    }
    BackHandler(enabled = destination != Destination.Home, onBack = back)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Выйти без сохранения?") },
            confirmButton = { TextButton(onClick = closeEditor) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить редактирование") } },
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!settingsFromEditor && bottomTabs.any { it.destination == destination }) {
                TabBar(selected = destination, onSelect = { destination = it })
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (destination) {
                Destination.Home -> HomeScreen(
                    viewModel = home,
                    onCreateProgram = createProgram,
                    onPrograms = { destination = Destination.Programs },
                    onWorkout = { id ->
                        workoutId = id
                        destination = Destination.Workout
                    },
                )
                Destination.Programs -> ProgramsScreen(
                    viewModel = programEditor,
                    onBack = back,
                    onCreate = createProgram,
                    onDetails = { id ->
                        programId = id
                        destination = Destination.ProgramDetails
                    },
                    onSelected = {
                        home.selectDay(null)
                        destination = Destination.Home
                    },
                )
                Destination.ProgramDetails -> ProgramDetailsScreen(programId, programEditor, back) { program ->
                    editorOrigin = Destination.ProgramDetails
                    programEditor.openEditor(program)
                    destination = Destination.ProgramEditor
                }
                Destination.ProgramEditor -> ProgramEditorScreen(
                    viewModel = programEditor,
                    onBack = back,
                    onEditDay = { destination = Destination.DayEditor },
                    onSettings = editorSettings,
                    onSaved = { destination = editorOrigin },
                )
                Destination.DayEditor -> TrainingDayEditorScreen(programEditor, back, editorSettings)
                Destination.Workout -> WorkoutScreen(
                    id = workoutId,
                    viewModel = workout,
                    onBack = back,
                    onFinished = { destination = Destination.WorkoutDetails },
                )
                Destination.History -> HistoryScreen(history) { id ->
                    workoutId = id
                    destination = Destination.WorkoutDetails
                }
                Destination.WorkoutDetails -> WorkoutDetailsScreen(workoutId, history, back)
                Destination.Settings -> SettingsScreen(settings, onBack = back.takeIf { settingsFromEditor }) {
                    settings.startUpdate(programEditor.editing && programEditor.hasChanges)
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab.destination,
                onClick = { onSelect(tab.destination) },
                label = { Text(tab.label) },
                icon = { FittyGlyph(tab.icon) },
            )
        }
    }
}
