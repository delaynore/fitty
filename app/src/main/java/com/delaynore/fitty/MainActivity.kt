package com.delaynore.fitty

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delaynore.fitty.navigation.FittyNavigation
import com.delaynore.fitty.ui.FittyTheme

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBar = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = systemBar, navigationBarStyle = systemBar)

        val fitty = application as FittyApplication
        container = fitty.appContainer(savedStateRegistry.consumeRestoredStateForKey(FITTY_SESSION_STATE))
        savedStateRegistry.registerSavedStateProvider(FITTY_SESSION_STATE, container::saveState)
        setContent {
            FittyTheme {
                FittyNavigation(container)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) container.activeWorkout.resetScreenState()
        super.onDestroy()
    }
}

private const val FITTY_SESSION_STATE = "fittySessionState"
