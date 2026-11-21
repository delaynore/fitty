package com.delaynore.fitty

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delaynore.fitty.data.programs.ProgramsRepository
import com.delaynore.fitty.data.workouts.WorkoutsRepository
import com.delaynore.fitty.navigation.FittyNavigation
import com.delaynore.fitty.ui.FittyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBar = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = systemBar, navigationBarStyle = systemBar)

        val database = (application as FittyApplication).database
        val programs = ProgramsRepository(database)
        val workouts = WorkoutsRepository(database)
        setContent {
            FittyTheme { FittyNavigation(programs, workouts) }
        }
    }
}
