package com.delaynore.fitty.data.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/delaynore/fitty/releases/latest"
private const val RELEASE_DOWNLOAD_PATH = "/delaynore/fitty/releases/download/"
private const val REQUEST_TIMEOUT_MILLIS = 10_000
private const val MAX_DOWNLOAD_REDIRECTS = 5
private const val CACHED_APK_PREFIX = "fitty-update-"
private const val APK_SUFFIX = ".apk"
private val apkName = Regex("""fitty-([1-9][0-9]*)\.apk""")

internal data class GitHubReleaseResponse(val status: Int, val body: String = "")

internal class GitHubUpdateService(
    private val context: Context,
    private val fetchApk: suspend (FittyUpdate, File, (Int) -> Unit) -> Unit = { update, file, progress ->
        downloadReleaseApk(update, file, progress)
    },
    private val fetchLatestRelease: () -> GitHubReleaseResponse = ::requestLatestRelease,
) : UpdateService {
    private val updatesDirectory = File(context.cacheDir, "updates")
    private val pruneLock = Mutex()
    private var prunedAtStartup = false

    override suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val response = try {
                fetchLatestRelease()
            } catch (_: IOException) {
                return@withContext UpdateCheckResult.Failed(UpdateCheckFailure.Network)
            }
            when (response.status) {
                HttpURLConnection.HTTP_OK -> parseRelease(response.body, installedVersionCode)
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.NoStableRelease
                else -> UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
            }
        }

    override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) = withContext(Dispatchers.IO) {
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

    override suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult {
        var pendingApk: File? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val apk = writeToCache {
                    updatesDirectory.mkdirs()
                    File.createTempFile(CACHED_APK_PREFIX, APK_SUFFIX, updatesDirectory)
                }
                pendingApk = apk
                fetchApk(update, apk, onProgress)
                currentCoroutineContext().ensureActive()
                @Suppress("DEPRECATION")
                val archive = context.packageManager.getPackageArchiveInfo(apk.path, 0)
                if (archive == null || archive.packageName != context.packageName ||
                    PackageInfoCompat.getLongVersionCode(archive) != update.versionCode ||
                    update.versionCode <= installedVersionCode || archive.versionName != update.versionName
                ) {
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

    override fun canRequestInstall() = context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission(launch: (Intent) -> Unit): Boolean = launchActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)),
        launch,
    )

    @Suppress("DEPRECATION")
    override fun openInstaller(apk: File, launch: (Intent) -> Unit): Boolean {
        if (!apk.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return launchActivity(install, launch)
    }

    private fun launchActivity(intent: Intent, launch: (Intent) -> Unit): Boolean = try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun parseRelease(body: String, installedVersionCode: Long): UpdateCheckResult {
    try {
        val release = JSONObject(body)
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) {
            return UpdateCheckResult.NoStableRelease
        }

        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).mapNotNull { parseApk(assets.getJSONObject(it)) }.singleOrNull()
            ?: return UpdateCheckResult.Failed(UpdateCheckFailure.MissingApk)
        if (apk.versionCode <= installedVersionCode) return UpdateCheckResult.UpToDate

        val versionName = (release.opt("name") as? String)?.takeIf { it.isNotBlank() }
            ?: return UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
        return UpdateCheckResult.Available(FittyUpdate(versionName, apk.versionCode, apk.url, apk.sizeBytes))
    } catch (_: JSONException) {
        return UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
    }
}

internal suspend fun downloadReleaseApk(
    update: FittyUpdate,
    destination: File,
    onProgress: (Int) -> Unit,
    openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) = coroutineScope {
    var url = URL(update.apkUrl)
    repeat(MAX_DOWNLOAD_REDIRECTS + 1) {
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) {
            throw ApkDownloadException(UpdateDownloadFailure.Delivery)
        }
        currentCoroutineContext().ensureActive()
        val connection = openConnection(url)
        val transfer = this
        val disconnectOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!transfer.isActive) connection.disconnect()
            }
        }
        try {
            connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = REQUEST_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Fitty")
            currentCoroutineContext().ensureActive()
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val expectedBytes = connection.contentLengthLong.takeIf { it >= 0 } ?: update.apkSizeBytes
                    var receivedBytes = 0L
                    var lastPercent = 0
                    if (expectedBytes != null && expectedBytes > 0) onProgress(lastPercent)
                    connection.inputStream.use { input ->
                        writeToCache {
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = try {
                                        input.read(buffer)
                                    } catch (failure: IOException) {
                                        throw ApkDownloadException(transferFailure(failure))
                                    }
                                    currentCoroutineContext().ensureActive()
                                    if (count == -1) break
                                    output.write(buffer, 0, count)
                                    receivedBytes += count
                                    if (expectedBytes != null && expectedBytes > 0) {
                                        val percent = (receivedBytes * 100.0 / expectedBytes).toInt().coerceAtMost(100)
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            onProgress(percent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (expectedBytes != null && receivedBytes != expectedBytes) {
                        throw ApkDownloadException(UpdateDownloadFailure.Delivery)
                    }
                    return@coroutineScope
                }
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw ApkDownloadException(UpdateDownloadFailure.Delivery)
                    url = URL(url, location)
                }
                else -> throw ApkDownloadException(UpdateDownloadFailure.Delivery)
            }
        } finally {
            disconnectOnCancel.cancel()
            connection.disconnect()
        }
    }
    throw ApkDownloadException(UpdateDownloadFailure.Delivery)
}

private class ApkDownloadException(val reason: UpdateDownloadFailure) : Exception()

private fun transferFailure(failure: IOException): UpdateDownloadFailure = when (failure) {
    is EOFException, is MalformedURLException, is ProtocolException -> UpdateDownloadFailure.Delivery
    else -> UpdateDownloadFailure.Network
}

private inline fun <T> writeToCache(write: () -> T): T = try {
    write()
} catch (_: IOException) {
    throw ApkDownloadException(UpdateDownloadFailure.LocalWrite)
}

private data class ReleaseApk(val versionCode: Long, val url: String, val sizeBytes: Long?)

private fun parseApk(asset: JSONObject): ReleaseApk? {
    val name = asset.optString("name")
    val versionCode = apkName.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    if (asset.optString("state") != "uploaded") return null

    val apkUrl = asset.optString("browser_download_url")
    val uri = try {
        URI(apkUrl)
    } catch (_: URISyntaxException) {
        return null
    }
    val path = uri.rawPath.orEmpty()
    if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null || uri.port != -1 ||
        uri.rawQuery != null || uri.rawFragment != null ||
        !path.startsWith(RELEASE_DOWNLOAD_PATH) || !path.endsWith("/$name")
    ) {
        return null
    }
    val sizeBytes = (asset.opt("size") as? Number)?.toLong()?.takeIf { it > 0 }
    return ReleaseApk(versionCode, apkUrl, sizeBytes)
}

private fun requestLatestRelease(): GitHubReleaseResponse {
    val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
        connection.readTimeout = REQUEST_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Fitty")
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        val status = connection.responseCode
        val body = if (status == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            ""
        }
        return GitHubReleaseResponse(status, body)
    } finally {
        connection.disconnect()
    }
}
