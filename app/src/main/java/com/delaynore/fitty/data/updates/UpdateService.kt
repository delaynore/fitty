package com.delaynore.fitty.data.updates

internal fun interface UpdateService {
    suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult
}

internal data class FittyUpdate(val versionName: String, val versionCode: Long, val apkUrl: String)

internal sealed interface UpdateCheckResult {
    data class Available(val update: FittyUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoStableRelease : UpdateCheckResult
    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult
}

internal enum class UpdateCheckFailure { Network, GitHub, MissingApk }
