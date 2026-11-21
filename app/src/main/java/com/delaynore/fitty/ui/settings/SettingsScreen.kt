package com.delaynore.fitty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel) {
    val check by viewModel.updateCheck.collectAsStateWithLifecycle()
    SettingsContent(viewModel.installedApp, check, viewModel::checkForUpdate)
}

@Composable
internal fun SettingsContent(installedApp: InstalledApp, check: UpdateCheckState, onCheck: () -> Unit) {
    ScreenColumn {
        PageHeader("Настройки")
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleMedium)
                Text("Fitty", style = MaterialTheme.typography.titleLarge)
                Text("Версия ${installedApp.versionName} · сборка ${installedApp.versionCode}")
                Button(onClick = onCheck, enabled = check != UpdateCheckState.Checking) {
                    Text("Проверить обновления")
                }
                Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (check) {
                        UpdateCheckState.Idle -> Unit
                        UpdateCheckState.Checking -> {
                            Text("Проверяем обновления…")
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        is UpdateCheckState.Checked -> UpdateCheckResultContent(check.result)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckResultContent(result: UpdateCheckResult) {
    when (result) {
        is UpdateCheckResult.Available -> {
            Text("Доступно обновление", style = MaterialTheme.typography.titleMedium)
            Text("Версия ${result.update.versionName} · сборка ${result.update.versionCode}")
        }
        UpdateCheckResult.UpToDate -> Text("Текущая сборка актуальна.")
        UpdateCheckResult.NoStableRelease -> Text("Стабильных релизов пока нет.")
        is UpdateCheckResult.Failed -> Text(
            text = when (result.reason) {
                UpdateCheckFailure.Network -> "Не удалось подключиться. Проверь интернет и попробуй ещё раз."
                UpdateCheckFailure.GitHub -> "Не удалось проверить обновления на GitHub. Попробуй ещё раз."
                UpdateCheckFailure.MissingApk -> "В стабильном релизе нет подходящего APK для Fitty. Попробуй ещё раз позже."
            },
            color = MaterialTheme.colorScheme.error,
        )
    }
}
