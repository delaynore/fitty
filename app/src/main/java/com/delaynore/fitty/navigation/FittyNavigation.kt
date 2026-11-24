package com.delaynore.fitty.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.delaynore.fitty.AppContainer
import com.delaynore.fitty.data.exercises.CatalogExercise
import com.delaynore.fitty.ui.FittyGlyph
import com.delaynore.fitty.ui.FittyIcon
import com.delaynore.fitty.ui.exercises.ExerciseCardContent
import com.delaynore.fitty.ui.exercises.ExerciseCatalogScreen
import com.delaynore.fitty.ui.exercises.ExerciseCatalogViewModel
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
import com.delaynore.fitty.ui.settings.SettingsScreen
import com.delaynore.fitty.ui.settings.SettingsViewModel
import com.delaynore.fitty.ui.settings.UpdateInstallerEffect
import com.delaynore.fitty.ui.workout.WorkoutScreen
import com.delaynore.fitty.ui.workout.WorkoutViewModel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

internal sealed interface TabRoute

// Serialization looks up object instances reflectively on the JVM.
@Serializable internal object HomeRoute : TabRoute
@Serializable internal object ProgramsRoute
@Serializable private data class ProgramDetailsRoute(val programId: Long)
@Serializable private data class ProgramEditorGraph(val programId: Long? = null)
@Serializable internal object ProgramEditorRoute
@Serializable private data class DayEditorRoute(val dayId: Long)
@Serializable private data class PickExerciseRoute(val dayId: Long, val replaceId: Long? = null)
@Serializable private data class PickExerciseCardRoute(val dayId: Long, val replaceId: Long?, val exerciseId: String)
@Serializable private data class CreateExerciseRoute(val dayId: Long, val name: String, val replaceId: Long?)
@Serializable private data class WorkoutRoute(val workoutId: Long)
@Serializable internal object HistoryRoute : TabRoute
@Serializable private data class WorkoutDetailsRoute(val workoutId: Long)
@Serializable internal object SettingsRoute : TabRoute
@Serializable internal object EditorSettingsRoute
@Serializable internal object BrowseCatalogRoute
@Serializable private data class ExerciseCardRoute(val exerciseId: String)
@Serializable private data class EditExerciseRoute(val exerciseId: String)

private data class BottomTab(val route: TabRoute, val label: String, val icon: FittyIcon)

private val bottomTabs = listOf(
    BottomTab(HomeRoute, "Главная", FittyIcon.Home),
    BottomTab(HistoryRoute, "История", FittyIcon.History),
    BottomTab(SettingsRoute, "Настройки", FittyIcon.Settings),
)

@Composable
internal fun FittyNavigation(container: AppContainer) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val selected = current?.destination
    val back: () -> Unit = { nav.popBackStack() }
    UpdateInstallerEffect(container.updates) {
        val destination = selected
        destination?.hasRoute<SettingsRoute>() == true || destination?.hasRoute<EditorSettingsRoute>() == true
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (bottomTabs.any { selected?.hasRoute(it.route::class) == true }) {
                TabBar(selected, nav::selectTab)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable<HomeRoute> {
                val home = viewModel { HomeViewModel(container.programs, container.workouts, container.activeWorkout::clear) }
                HomeScreen(
                    viewModel = home,
                    onCreateProgram = { nav.navigate(ProgramEditorGraph()) },
                    onPrograms = { nav.navigate(ProgramsRoute) },
                    onWorkout = { nav.navigate(WorkoutRoute(it)) },
                )
            }
            composable<ProgramsRoute> { entry ->
                val editor = viewModel { ProgramEditorViewModel(container.programs) }
                val homeEntry = remember(entry) { nav.getBackStackEntry<HomeRoute>() }
                val home = viewModel(viewModelStoreOwner = homeEntry) {
                    HomeViewModel(container.programs, container.workouts, container.activeWorkout::clear)
                }
                ProgramsScreen(
                    viewModel = editor,
                    onBack = back,
                    onCreate = { nav.navigate(ProgramEditorGraph()) },
                    onDetails = { nav.navigate(ProgramDetailsRoute(it)) },
                    onSelected = {
                        home.selectDay(null)
                        nav.popBackStack<HomeRoute>(inclusive = false)
                    },
                )
            }
            composable<ProgramDetailsRoute> { entry ->
                val id = entry.toRoute<ProgramDetailsRoute>().programId
                val editor = viewModel { ProgramEditorViewModel(container.programs) }
                ProgramDetailsScreen(id, editor, back) { nav.navigate(ProgramEditorGraph(it.id)) }
            }
            navigation<ProgramEditorGraph>(startDestination = ProgramEditorRoute) {
                composable<ProgramEditorRoute> { entry ->
                    val editor = programEditor(nav, entry, container)
                    ProgramEditorDestination(editor, nav)
                }
                composable<DayEditorRoute> { entry ->
                    val (dayId) = entry.toRoute<DayEditorRoute>()
                    val editor = programEditor(nav, entry, container)
                    val state by editor.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(state.editing, dayId) {
                        if (state.editing && editor.uiState.value.editingDayId != dayId) {
                            if (editor.uiState.value.visibleDays.any { it.id == dayId }) editor.openDay(dayId)
                            else nav.popBackStack<ProgramEditorRoute>(inclusive = false)
                        }
                    }
                    if (state.editingDayId == dayId) {
                        TrainingDayEditorScreen(editor, back, { nav.navigate(EditorSettingsRoute) }) { replaceId ->
                            nav.navigate(PickExerciseRoute(dayId, replaceId))
                        }
                    }
                }
                composable<PickExerciseRoute> { entry ->
                    val (dayId, replaceId) = entry.toRoute<PickExerciseRoute>()
                    val editor = programEditor(nav, entry, container)
                    val catalog = viewModel { ExerciseCatalogViewModel(container.catalog) }
                    val select: (CatalogExercise) -> Unit = {
                        editor.selectExercise(it, replaceId)
                        nav.popBackStack(DayEditorRoute(dayId), inclusive = false)
                    }
                    ExerciseCatalogScreen(
                        viewModel = catalog,
                        onBack = back,
                        onOpenExercise = { nav.navigate(PickExerciseCardRoute(dayId, replaceId, it)) },
                        onSelect = select.takeIf { replaceId == null },
                        onCreate = { nav.navigate(CreateExerciseRoute(dayId, it, replaceId)) },
                    )
                }
                composable<PickExerciseCardRoute> { entry ->
                    val (dayId, replaceId, exerciseId) = entry.toRoute<PickExerciseCardRoute>()
                    val editor = programEditor(nav, entry, container)
                    val catalog = viewModel { ExerciseCatalogViewModel(container.catalog) }
                    CatalogCard(catalog, exerciseId, back, replacing = replaceId != null, onSelect = {
                        editor.selectExercise(it, replaceId)
                        nav.popBackStack(DayEditorRoute(dayId), inclusive = false)
                    })
                }
                composable<CreateExerciseRoute> { entry ->
                    val route = entry.toRoute<CreateExerciseRoute>()
                    val editor = programEditor(nav, entry, container)
                    val exercise = viewModel { ExerciseEditorViewModel(container.catalog) }
                    LaunchedEffect(entry) {
                        if (exercise.uiState.value.draft == null && editor.uiState.value.editing) {
                            if (entry.savedStateHandle.get<Boolean>(EDITOR_OPENED) == true) nav.popBackStack()
                            else {
                                exercise.open(route.name)
                                entry.savedStateHandle[EDITOR_OPENED] = true
                            }
                        }
                    }
                    ExerciseEditorDestination(exercise, back) {
                        editor.selectExercise(it, route.replaceId)
                        nav.popBackStack(DayEditorRoute(route.dayId), inclusive = false)
                    }
                }
                composable<EditorSettingsRoute> { entry ->
                    val editor = programEditor(nav, entry, container)
                    val settings = viewModel { SettingsViewModel(container.updates) }
                    SettingsScreen(settings, back, { nav.navigate(BrowseCatalogRoute) }) {
                        settings.updates.startUpdate(editor.uiState.value.hasChanges)
                    }
                }
            }
            composable<WorkoutRoute> { entry ->
                val id = entry.toRoute<WorkoutRoute>().workoutId
                val workout = viewModel { WorkoutViewModel(container.activeWorkout) }
                WorkoutScreen(id, workout, back) {
                    if (nav.currentBackStackEntry == entry) {
                        nav.popBackStack()
                        nav.selectTab(HistoryRoute)
                        nav.popBackStack<HistoryRoute>(inclusive = false)
                        nav.navigate(WorkoutDetailsRoute(id))
                    }
                }
            }
            composable<HistoryRoute> {
                val history = viewModel { HistoryViewModel(container.workouts) }
                HistoryScreen(history) { nav.navigate(WorkoutDetailsRoute(it)) }
            }
            composable<WorkoutDetailsRoute> { entry ->
                val history = viewModel { HistoryViewModel(container.workouts) }
                WorkoutDetailsScreen(entry.toRoute<WorkoutDetailsRoute>().workoutId, history, back)
            }
            composable<SettingsRoute> {
                val settings = viewModel { SettingsViewModel(container.updates) }
                SettingsScreen(settings, null, { nav.navigate(BrowseCatalogRoute) }) {
                    settings.updates.startUpdate(hasUnsavedInput = false)
                }
            }
            composable<BrowseCatalogRoute> {
                val catalog = viewModel { ExerciseCatalogViewModel(container.catalog) }
                ExerciseCatalogScreen(catalog, back, onOpenExercise = { nav.navigate(ExerciseCardRoute(it)) })
            }
            composable<ExerciseCardRoute> { entry ->
                val id = entry.toRoute<ExerciseCardRoute>().exerciseId
                val catalog = viewModel { ExerciseCatalogViewModel(container.catalog) }
                CatalogCard(catalog, id, back, onEdit = { nav.navigate(EditExerciseRoute(it.id)) })
            }
            composable<EditExerciseRoute> { entry ->
                val id = entry.toRoute<EditExerciseRoute>().exerciseId
                val exercise = viewModel { ExerciseEditorViewModel(container.catalog) }
                LaunchedEffect(entry) {
                    if (exercise.uiState.value.draft != null) return@LaunchedEffect
                    val stored = if (entry.savedStateHandle.get<Boolean>(EDITOR_OPENED) == true) null
                    else container.catalog.allExercises.first().find { it.id == id && it.isCustom }
                    if (stored == null) nav.popBackStack()
                    else {
                        exercise.open(stored)
                        entry.savedStateHandle[EDITOR_OPENED] = true
                    }
                }
                ExerciseEditorDestination(exercise, back) { nav.popBackStack() }
            }
        }
    }
}

private const val EDITOR_OPENED = "editorOpened"

private fun NavHostController.selectTab(route: TabRoute) {
    if (currentDestination?.hasRoute(route::class) == true) return
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun programEditor(
    nav: NavHostController,
    entry: NavBackStackEntry,
    container: AppContainer,
): ProgramEditorViewModel {
    val parent = remember(entry) { nav.getBackStackEntry<ProgramEditorGraph>() }
    val editor = viewModel<ProgramEditorViewModel>(viewModelStoreOwner = parent) {
        ProgramEditorViewModel(container.programs)
    }
    LaunchedEffect(entry) {
        if (editor.uiState.value.editing) return@LaunchedEffect
        val id = parent.toRoute<ProgramEditorGraph>().programId
        val wasOpened = parent.savedStateHandle.get<Boolean>(EDITOR_OPENED) == true
        val program = if (id == null || wasOpened) null else container.programs.observeProgram(id).first()
        if (wasOpened || (id != null && program == null)) {
            nav.popBackStack<ProgramEditorGraph>(inclusive = true)
        } else {
            editor.openEditor(program)
            parent.savedStateHandle[EDITOR_OPENED] = true
        }
    }
    return editor
}

@Composable
private fun ProgramEditorDestination(editor: ProgramEditorViewModel, nav: NavHostController) {
    val state by editor.uiState.collectAsStateWithLifecycle()
    if (!state.editing) {
        CircularProgressIndicator()
        return
    }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val exit: () -> Unit = { nav.popBackStack<ProgramEditorGraph>(inclusive = true) }
    val back = {
        val current = editor.uiState.value
        if (!current.saving) {
            if (current.hasChanges) confirmDiscard = true else exit()
        }
    }
    BackHandler(onBack = back)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Выйти без сохранения?") },
            confirmButton = { TextButton(onClick = exit) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Продолжить редактирование") } },
        )
    }
    ProgramEditorScreen(
        viewModel = editor,
        onBack = back,
        onEditDay = { editor.uiState.value.editingDayId?.let { nav.navigate(DayEditorRoute(it)) } },
        onSettings = { nav.navigate(EditorSettingsRoute) },
        onSaved = exit,
    )
}

@Composable
private fun ExerciseEditorDestination(
    editor: ExerciseEditorViewModel,
    onBack: () -> Unit,
    onSaved: (CatalogExercise) -> Unit,
) {
    val state by editor.uiState.collectAsStateWithLifecycle()
    val back = { if (editor.uiState.value.draft?.saving != true) onBack() }
    BackHandler(onBack = back)
    LaunchedEffect(state.savedExercise) {
        state.savedExercise?.let(onSaved)
    }
    ExerciseEditorScreen(editor, back)
}

@Composable
private fun CatalogCard(
    catalog: ExerciseCatalogViewModel,
    id: String,
    onBack: () -> Unit,
    replacing: Boolean = false,
    onSelect: ((CatalogExercise) -> Unit)? = null,
    onEdit: ((CatalogExercise) -> Unit)? = null,
) {
    val state by catalog.uiState.collectAsStateWithLifecycle()
    val exercises = state.exercises
    val exercise = exercises?.find { it.id == id }
    LaunchedEffect(exercises != null, exercise != null) {
        if (exercises != null && exercise == null) onBack()
    }
    if (exercise == null) {
        CircularProgressIndicator()
        return
    }
    ExerciseCardContent(
        exercise,
        onBack = onBack,
        selectionLabel = if (replacing) "Заменить упражнение" else "Добавить упражнение",
        onSelect = onSelect?.let { { it(exercise) } },
        onEdit = onEdit?.takeIf { exercise.isCustom }?.let { { it(exercise) } },
    )
}

@Composable
private fun TabBar(selected: NavDestination?, onSelect: (TabRoute) -> Unit) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected?.hasRoute(tab.route::class) == true,
                onClick = { onSelect(tab.route) },
                label = { Text(tab.label) },
                icon = { FittyGlyph(tab.icon) },
            )
        }
    }
}
