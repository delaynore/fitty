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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delaynore.fitty.data.exercises.ExerciseCatalogRepository
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.exercises.ExerciseCatalog
import com.delaynore.fitty.ui.exercises.ExerciseEditorScreen
import com.delaynore.fitty.ui.exercises.ExerciseEditorViewModel
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

private enum class Destination {
    Home, Programs, ProgramDetails, ProgramEditor, DayEditor, DayEditorCatalog, CreateExercise,
    Workout, History, WorkoutDetails, Settings, SettingsCatalog, EditExerciseDetails,
}

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
    catalog: ExerciseCatalogRepository,
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
    var legacyExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val home = viewModel { HomeViewModel(programs, workouts) }
    val programEditor = viewModel { ProgramEditorViewModel(programs) }
    val exerciseEditor = viewModel { ExerciseEditorViewModel(catalog) }
    val catalogExercises by catalog.allExercises.collectAsStateWithLifecycle(initialValue = null)
    val history = viewModel { HistoryViewModel(workouts) }
    val settings = viewModel { SettingsViewModel(installedApp, updateService, workouts, createSavedStateHandle()) }
    UpdateInstallerEffect(settings) { destination == Destination.Settings }
    val context = LocalContext.current
    val workout = viewModel {
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        WorkoutViewModel(workouts, createSavedStateHandle(), bootCount, restTimerClock, catalog)
    }

    val closeEditor = {
        confirmDiscard = false
        legacyExerciseId = null
        exerciseEditor.close()
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
            Destination.DayEditorCatalog -> {
                legacyExerciseId = null
                destination = Destination.DayEditor
            }
            Destination.CreateExercise -> if (exerciseEditor.state?.saving != true) {
                exerciseEditor.close()
                destination = Destination.DayEditorCatalog
            }
            Destination.ProgramEditor -> if (!programEditor.saving) {
                if (programEditor.hasChanges) confirmDiscard = true else closeEditor()
            }
            Destination.ProgramDetails -> destination = Destination.Programs
            Destination.WorkoutDetails -> destination = Destination.History
            Destination.SettingsCatalog -> destination = Destination.Settings
            Destination.EditExerciseDetails -> if (exerciseEditor.state?.saving != true) {
                exerciseEditor.close()
                destination = Destination.SettingsCatalog
            }
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
        val inEditor = destination == Destination.ProgramEditor ||
            destination == Destination.DayEditor || destination == Destination.DayEditorCatalog ||
            destination == Destination.CreateExercise
        if (inEditor && !programEditor.editing) {
            closeEditor()
        } else if (destination == Destination.EditExerciseDetails && exerciseEditor.state == null) {
            destination = Destination.SettingsCatalog
        }
    }
    val savedExercise = exerciseEditor.savedExercise
    LaunchedEffect(savedExercise, destination) {
        if (savedExercise != null) {
            when (destination) {
                Destination.CreateExercise -> if (programEditor.editing) {
                    exerciseEditor.close()
                    programEditor.selectExercise(savedExercise, legacyExerciseId)
                    legacyExerciseId = null
                    destination = Destination.DayEditor
                }
                Destination.EditExerciseDetails -> {
                    exerciseEditor.close()
                    destination = Destination.SettingsCatalog
                }
                else -> Unit
            }
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
                Destination.DayEditor, Destination.DayEditorCatalog, Destination.CreateExercise -> {
                    val editorState = rememberSaveableStateHolder()
                    when (destination) {
                        Destination.DayEditor -> editorState.SaveableStateProvider("day") {
                            TrainingDayEditorScreen(programEditor, back, editorSettings) { existingExerciseId ->
                                editorState.removeState("catalog")
                                legacyExerciseId = existingExerciseId
                                destination = Destination.DayEditorCatalog
                            }
                        }
                        Destination.DayEditorCatalog -> editorState.SaveableStateProvider("catalog") {
                            ExerciseCatalog(
                                exercises = catalogExercises,
                                onBack = back,
                                replacingLegacyExercise = legacyExerciseId != null,
                                onSelect = { exercise ->
                                    if (destination == Destination.DayEditorCatalog) {
                                        programEditor.selectExercise(exercise, legacyExerciseId)
                                        back()
                                    }
                                },
                                onCreate = { name ->
                                    exerciseEditor.open(name)
                                    destination = Destination.CreateExercise
                                },
                            )
                        }
                        Destination.CreateExercise -> ExerciseEditorScreen(exerciseEditor, back)
                        else -> Unit
                    }
                }
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
                Destination.Settings -> SettingsScreen(
                    viewModel = settings,
                    onBack = back.takeIf { settingsFromEditor },
                    onBrowseExercises = { destination = Destination.SettingsCatalog },
                    onUpdate = { settings.startUpdate(programEditor.editing && programEditor.hasChanges) },
                )
                Destination.SettingsCatalog, Destination.EditExerciseDetails -> {
                    val catalogState = rememberSaveableStateHolder()
                    if (destination == Destination.SettingsCatalog) {
                        catalogState.SaveableStateProvider("catalog") {
                            ExerciseCatalog(
                                exercises = catalogExercises,
                                onBack = back,
                                onEdit = { exercise ->
                                    exerciseEditor.open(exercise)
                                    destination = Destination.EditExerciseDetails
                                },
                            )
                        }
                    } else {
                        ExerciseEditorScreen(exerciseEditor, back)
                    }
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
