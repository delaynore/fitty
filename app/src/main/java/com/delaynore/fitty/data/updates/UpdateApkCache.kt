package com.delaynore.fitty.data.updates

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.delaynore.fitty.platform.updates.getPackageArchiveInfoCompat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CACHED_APK_PREFIX = "fitty-update-"
private const val APK_SUFFIX = ".apk"

internal class UpdateApkCache(
    cacheDirectory: File,
    private val matchesRelease: (File, FittyUpdate) -> Boolean,
) {
    constructor(context: Context) : this(context.cacheDir, { apk, update ->
        val archive = context.packageManager.getPackageArchiveInfoCompat(apk.path)
        archive != null && archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) == update.versionCode &&
            archive.versionName == update.versionName
    })

    private val updatesDirectory = File(cacheDirectory, "updates")
    private val pruneLock = Mutex()
    private var prunedAtStartup = false

    suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = withContext(Dispatchers.IO) {
        pruneLock.withLock {
            if (prunedAtStartup) return@withLock
            updatesDirectory.listFiles { file ->
                file.isFile && file.name.startsWith(CACHED_APK_PREFIX) && file.name.endsWith(APK_SUFFIX)
            }?.forEach { apk ->
                if (apk != pendingPermissionApk) apk.delete()
            }
            prunedAtStartup = true
        }
    }

    suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        fetchApk: suspend (File) -> Unit,
    ): UpdateDownloadResult {
        var pendingApk: File? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val apk = writeToCache {
                    updatesDirectory.mkdirs()
                    File.createTempFile(CACHED_APK_PREFIX, APK_SUFFIX, updatesDirectory)
                }
                pendingApk = apk
                fetchApk(apk)
                currentCoroutineContext().ensureActive()
                if (update.versionCode <= installedVersionCode || !matchesRelease(apk, update)) {
                    return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk)
                }
                UpdateDownloadResult.Downloaded(apk)
            }
            // Keep ownership until withContext delivers the result to the caller's dispatcher.
            if (result is UpdateDownloadResult.Downloaded) pendingApk = null
            return result
        } catch (failure: ApkDownloadException) {
            return UpdateDownloadResult.Failed(failure.reason)
        } catch (failure: IOException) {
            return UpdateDownloadResult.Failed(transferFailure(failure))
        } finally {
            pendingApk?.let { apk ->
                withContext(NonCancellable + Dispatchers.IO) { apk.delete() }
            }
        }
    }

    suspend fun deleteApk(apk: File) = withContext(NonCancellable + Dispatchers.IO) { apk.delete(); Unit }
}

internal inline fun <T> writeToCache(write: () -> T): T = try {
    write()
} catch (_: IOException) {
    throw ApkDownloadException(UpdateDownloadFailure.LocalWrite)
}
