package com.delaynore.fitty

import android.os.Bundle
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.delaynore.fitty.data.exercises.RoomExerciseCatalogRepository
import com.delaynore.fitty.data.programs.RoomProgramsRepository
import com.delaynore.fitty.data.workouts.RoomWorkoutsRepository
import com.delaynore.fitty.ui.settings.InstalledApp
import com.delaynore.fitty.ui.settings.UpdateCoordinator
import com.delaynore.fitty.ui.workout.ActiveWorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class AppContainer(application: FittyApplication, restoredState: Bundle?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val programs = RoomProgramsRepository(application.database)
    val workouts = RoomWorkoutsRepository(application.database)
    val catalog = RoomExerciseCatalogRepository(application.database)

    val updates by lazy {
        @Suppress("DEPRECATION")
        val info = application.packageManager.getPackageInfo(application.packageName, 0)
        UpdateCoordinator(
            InstalledApp(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info)),
            application.updateService,
            workouts,
            scope,
            restoredState?.getString("pendingUpdatePermission"),
        )
    }

    val activeWorkout = ActiveWorkoutState(
        workouts,
        Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, -1),
        application::workoutTimerElapsedRealtime,
        application.setTimerSignals,
        catalog,
        scope,
        restoredState?.getBundle("workoutTimers"),
    )

    fun saveState() = Bundle().apply {
        putBundle("workoutTimers", activeWorkout.saveTimers())
        putString("pendingUpdatePermission", updates.pendingPermissionPath)
    }

    fun close() = scope.cancel()
}
