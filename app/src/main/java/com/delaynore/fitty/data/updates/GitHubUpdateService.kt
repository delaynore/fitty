package com.delaynore.fitty.data.updates

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/delaynore/fitty/releases/latest"
private const val RELEASE_DOWNLOAD_PATH = "/delaynore/fitty/releases/download/"
private const val REQUEST_TIMEOUT_MILLIS = 10_000
private const val MAX_DOWNLOAD_REDIRECTS = 5
private val apkName = Regex("""fitty-([1-9][0-9]*)\.apk""")

internal data class GitHubReleaseResponse(val status: Int, val body: String = "")

internal class GitHubUpdateService(
    context: Context,
    private val fetchApk: suspend (FittyUpdate, File, (Int) -> Unit) -> Unit = { update, file, progress ->
        downloadReleaseApk(update, file, progress)
    },
) : UpdateService {
    private val cache = UpdateApkCache(context)
    private val installer = AndroidUpdateInstaller(context)

    override suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            checkLatestRelease(installedVersionCode, ::requestLatestRelease)
        }

    override suspend fun pruneAbandonedDownloads(pendingPermissionApk: File?) =
        cache.pruneAbandonedDownloads(pendingPermissionApk)

    override suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult =
        cache.download(update, installedVersionCode) { apk -> fetchApk(update, apk, onProgress) }

    override fun canRequestInstall() = installer.canRequestInstall()

    override fun requestInstallPermission(launch: (Intent) -> Unit) =
        installer.requestInstallPermission(launch)

    override fun openInstaller(apk: File, launch: (Intent) -> Unit) = installer.openInstaller(apk, launch)

}

internal fun checkLatestRelease(installedVersionCode: Long, fetchLatestRelease: () -> GitHubReleaseResponse): UpdateCheckResult {
    val response = try {
        fetchLatestRelease()
    } catch (_: IOException) {
        return UpdateCheckResult.Failed(UpdateCheckFailure.Network)
    }
    return when (response.status) {
        HttpURLConnection.HTTP_OK -> parseRelease(response.body, installedVersionCode)
        HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.NoStableRelease
        else -> UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
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
            connection.configureReleaseRequest()
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

internal class ApkDownloadException(val reason: UpdateDownloadFailure) : Exception()

internal fun transferFailure(failure: IOException): UpdateDownloadFailure = when (failure) {
    is EOFException, is MalformedURLException, is ProtocolException -> UpdateDownloadFailure.Delivery
    else -> UpdateDownloadFailure.Network
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
        connection.configureReleaseRequest()
        connection.setRequestProperty("Accept", "application/vnd.github+json")
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

private fun HttpURLConnection.configureReleaseRequest() {
    connectTimeout = REQUEST_TIMEOUT_MILLIS
    readTimeout = REQUEST_TIMEOUT_MILLIS
    instanceFollowRedirects = false
    setRequestProperty("User-Agent", "Fitty")
}
