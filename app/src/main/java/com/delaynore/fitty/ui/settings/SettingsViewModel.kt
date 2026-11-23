package com.delaynore.fitty.ui.settings

import androidx.lifecycle.ViewModel

internal class SettingsViewModel(val updates: UpdateCoordinator) : ViewModel() {
    val uiState = updates.uiState
}
