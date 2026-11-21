package com.delaynore.fitty.data.updates

import android.content.Intent
import java.io.File

internal interface UpdateService {
    suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult
    suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?)
    suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        onProgress: (percent: Int) -> Unit = {},
    ): UpdateDownloadResult
    fun canRequestInstall(): Boolean
    fun requestInstallPermission(launch: (Intent) -> Unit): Boolean
    fun openInstaller(apk: File, launch: (Intent) -> Unit): Boolean
}

internal data class FittyUpdate(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val apkSizeBytes: Long? = null,
)

internal sealed interface UpdateCheckResult {
    data class Available(val update: FittyUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoStableRelease : UpdateCheckResult
    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult
}

internal enum class UpdateCheckFailure { Network, GitHub, MissingApk }

internal sealed interface UpdateDownloadResult {
    /** A validated APK owned by this download's caller. */
    data class Downloaded(val apk: File) : UpdateDownloadResult
    data class Failed(val reason: UpdateDownloadFailure) : UpdateDownloadResult
}

internal enum class UpdateDownloadFailure { Network, Delivery, LocalWrite, InvalidApk }
