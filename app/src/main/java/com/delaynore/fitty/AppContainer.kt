package com.delaynore.fitty

import android.os.Bundle
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.delaynore.fitty.platform.updates.getPackageInfoCompat
import com.delaynore.fitty.data.exercises.RoomExerciseCatalogRepository
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.workouts.RoomWorkoutsRepository
import com.delaynore.fitty.ui.settings.InstalledApp
import com.delaynore.fitty.ui.settings.UpdateCoordinator
import com.delaynore.fitty.ui.workout.ActiveWorkoutState
import com.delaynore.fitty.platform.workout.SharedPreferencesWorkoutTimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class AppContainer(application: FittyApplication, restoredState: Bundle?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val programs = RoomProgramsRepository(application.database)
    val workouts = RoomWorkoutsRepository(application.database)
    val catalog = RoomExerciseCatalogRepository(application.database)
    val updateInstaller = application.updateInstaller

    val updates by lazy {
        val info = application.packageManager.getPackageInfoCompat(application.packageName)
        UpdateCoordinator(
            InstalledApp(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info)),
            application.updateService,
            application.updateInstaller,
            application.updateAttemptStore,
            workouts,
            scope,
            restoredState?.getString("pendingUpdatePermission"),
            restoredState?.getLong("pendingUpdatePermissionVersion")?.takeIf { it > 0 },
        )
    }

    val activeWorkout = ActiveWorkoutState(
        workouts,
        Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, -1),
        application::workoutTimerElapsedRealtime,
        application.setTimerSignals,
        catalog,
        scope,
        SharedPreferencesWorkoutTimerStore(application, application.databaseName),
    )

    fun saveState() = Bundle().apply {
        putString("pendingUpdatePermission", updates.pendingPermissionPath)
        updates.pendingPermissionVersionCode?.let { putLong("pendingUpdatePermissionVersion", it) }
    }

    fun close() = scope.cancel()
}
