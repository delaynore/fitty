package com.delaynore.fitty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.ui.PageHeader
import com.delaynore.fitty.ui.ScreenColumn

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)?,
    onBrowseExercises: () -> Unit,
    onUpdate: () -> Unit,
) {
    val check by viewModel.updateCheck.collectAsStateWithLifecycle()
    val install by viewModel.updateInstall.collectAsStateWithLifecycle()
    SettingsContent(
        installedApp = viewModel.installedApp,
        check = check,
        install = install,
        onBack = onBack,
        onBrowseExercises = onBrowseExercises,
        onCheck = viewModel::checkForUpdate,
        onUpdate = onUpdate,
        onConfirmDownload = viewModel::confirmDownload,
        onDismissWarning = viewModel::dismissWarning,
        onCancelDownload = viewModel::cancelDownload,
        onContinueInstallation = viewModel::continueInstallation,
    )
}

@Composable
internal fun SettingsContent(
    installedApp: InstalledApp,
    check: UpdateCheckState,
    install: UpdateInstallState,
    onBack: (() -> Unit)?,
    onBrowseExercises: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onConfirmDownload: () -> Unit,
    onDismissWarning: () -> Unit,
    onCancelDownload: () -> Unit,
    onContinueInstallation: () -> Unit,
) {
    ScreenColumn {
        PageHeader("Настройки", onBack)
        OutlinedButton(onClick = onBrowseExercises, modifier = Modifier.fillMaxWidth()) {
            Text("Каталог упражнений")
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleMedium)
                Text("Fitty", style = MaterialTheme.typography.titleLarge)
                Text("Версия ${installedApp.versionName} · сборка ${installedApp.versionCode}")
                Button(onClick = onCheck, enabled = check != UpdateCheckState.Checking && install.canCheckForUpdates) {
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
                    UpdateInstallContent(install, onCancelDownload, onContinueInstallation)
                    if (check is UpdateCheckState.Checked && check.result is UpdateCheckResult.Available &&
                        install.canStartAnotherUpdate
                    ) {
                        Button(onClick = onUpdate) {
                            Text(if (install is UpdateInstallState.DownloadFailed) "Повторить загрузку" else "Обновить")
                        }
                    }
                }
            }
        }
    }
    if (install == UpdateInstallState.Warning) {
        AlertDialog(
            onDismissRequest = onDismissWarning,
            title = { Text("Продолжить обновление?") },
            text = { Text("Сохранённые программы и записанные подходы останутся. Несохранённый ввод может пропасть.") },
            confirmButton = { TextButton(onClick = onConfirmDownload) { Text("Продолжить") } },
            dismissButton = { TextButton(onClick = onDismissWarning) { Text("Назад") } },
        )
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

@Composable
private fun UpdateInstallContent(
    state: UpdateInstallState,
    onCancelDownload: () -> Unit,
    onContinueInstallation: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    when (state) {
        is UpdateInstallState.Downloading -> {
            Text("Скачиваем обновление…")
            val percent = state.percent
            if (percent == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancelDownload) { Text("Отменить") }
        }
        is UpdateInstallState.PermissionDenied -> {
            Text("Чтобы продолжить, разреши Android устанавливать обновления из Fitty.")
            Button(onClick = onContinueInstallation) { Text("Разрешить установку") }
        }
        is UpdateInstallState.DownloadFailed -> Text(
            text = when (state.reason) {
                UpdateDownloadFailure.Network -> "Не удалось скачать обновление. Проверь интернет и попробуй ещё раз."
                UpdateDownloadFailure.Delivery -> "Не удалось получить APK целиком с GitHub. Попробуй ещё раз позже."
                UpdateDownloadFailure.LocalWrite -> "Не удалось сохранить APK. Освободи место на устройстве и попробуй ещё раз."
                UpdateDownloadFailure.InvalidApk -> "Скачанный APK повреждён или не соответствует релизу Fitty."
            },
            color = error,
        )
        UpdateInstallState.WorkoutCheckFailed -> Text("Не удалось проверить, есть ли незавершённая тренировка.", color = error)
        UpdateInstallState.InstallerUnavailable -> Text("Не удалось открыть установщик Android.", color = error)
        UpdateInstallState.InstallCancelled -> Text("Установка отменена.")
        UpdateInstallState.InstallFailed -> Text("Android не смог установить обновление.", color = error)
        else -> Unit
    }
}
