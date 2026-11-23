package com.delaynore.fitty

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.PackageInfoCompat
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.navigation.FittyNavigation
import com.delaynore.fitty.ui.FittyTheme
import com.delaynore.fitty.ui.settings.InstalledApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBar = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = systemBar, navigationBarStyle = systemBar)

        val fitty = application as FittyApplication
        val database = fitty.database
        val programs = ProgramsRepository(database)
        val workouts = WorkoutsRepository(database)
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val installedApp = InstalledApp(packageInfo.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(packageInfo))
        setContent {
            FittyTheme {
                FittyNavigation(
                    programs, workouts, fitty::workoutTimerElapsedRealtime, installedApp, fitty.updateService, fitty.setTimerSignals,
                )
            }
        }
    }
}
