package com.delaynore.fitty.data.updates

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

internal interface UpdateService {
    suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult
    suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?)
    suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        onProgress: (percent: Int) -> Unit = {},
    ): UpdateDownloadResult
    suspend fun deleteApk(apk: File) { withContext(NonCancellable + Dispatchers.IO) { apk.delete() } }
}

internal interface UpdateSessionManager {
    val sessionUpdates: Flow<Unit> get() = emptyFlow()
    fun canRequestInstall(): Boolean
    suspend fun commitInstall(apk: File, expectedVersionCode: Long): InstallSession?
    suspend fun abandonSession(sessionId: Int)
    fun hasSession(sessionId: Int): Boolean
    fun hasConfirmation(sessionId: Int, requestId: Long): Boolean
}

internal data class InstallSession(val sessionId: Int, val requestId: Long)

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
