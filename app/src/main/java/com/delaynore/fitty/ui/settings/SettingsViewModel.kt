package com.delaynore.fitty.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class InstalledApp(val versionName: String, val versionCode: Long)

internal sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Checked(val result: UpdateCheckResult) : UpdateCheckState
}

internal class SettingsViewModel(
    val installedApp: InstalledApp,
    private val updateService: UpdateService,
) : ViewModel() {
    private val check = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheck = check.asStateFlow()

    fun checkForUpdate() {
        if (check.value == UpdateCheckState.Checking) return
        check.value = UpdateCheckState.Checking
        viewModelScope.launch {
            check.value = UpdateCheckState.Checked(updateService.checkForUpdate(installedApp.versionCode))
        }
    }
}
