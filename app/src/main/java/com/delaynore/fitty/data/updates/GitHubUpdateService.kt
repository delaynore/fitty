package com.delaynore.fitty.data.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
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
    private val context: Context,
    private val fetchApk: suspend (String, File, (Int) -> Unit) -> Unit = ::downloadReleaseApk,
    private val fetchLatestRelease: () -> GitHubReleaseResponse = ::requestLatestRelease,
) : UpdateService {
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

    override suspend fun download(
        update: FittyUpdate,
        installedVersionCode: Long,
        onProgress: (Int) -> Unit,
    ): UpdateDownloadResult {
        var pendingApk: File? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "updates")
                directory.mkdirs()
                val apk = File.createTempFile("fitty-update-", ".apk", directory)
                pendingApk = apk
                fetchApk(update.apkUrl, apk, onProgress)
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
        } catch (_: IOException) {
            return UpdateDownloadResult.Failed(UpdateDownloadFailure.Unavailable)
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
        val (versionCode, apkUrl) = apk
        if (versionCode <= installedVersionCode) return UpdateCheckResult.UpToDate

        val versionName = (release.opt("name") as? String)?.takeIf { it.isNotBlank() }
            ?: return UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
        return UpdateCheckResult.Available(FittyUpdate(versionName, versionCode, apkUrl))
    } catch (_: JSONException) {
        return UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
    }
}

private suspend fun downloadReleaseApk(apkUrl: String, destination: File, onProgress: (Int) -> Unit) = coroutineScope {
    var url = URL(apkUrl)
    repeat(MAX_DOWNLOAD_REDIRECTS + 1) {
        if (url.protocol != "https" || url.host.isBlank() || url.userInfo != null) {
            throw IOException("Release download requires HTTPS")
        }
        currentCoroutineContext().ensureActive()
        val connection = url.openConnection() as HttpsURLConnection
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
                    val expectedBytes = connection.contentLengthLong
                    var receivedBytes = 0L
                    var lastPercent = 0
                    if (expectedBytes > 0) onProgress(lastPercent)
                    connection.inputStream.use { input ->
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                currentCoroutineContext().ensureActive()
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                receivedBytes += count
                                if (expectedBytes > 0) {
                                    val percent = (receivedBytes * 100.0 / expectedBytes).toInt().coerceAtMost(100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                    if (expectedBytes >= 0 && receivedBytes != expectedBytes) throw IOException("Incomplete download")
                    return@coroutineScope
                }
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                    url = URL(url, location)
                }
                else -> throw IOException("GitHub could not deliver the APK")
            }
        } finally {
            disconnectOnCancel.cancel()
            connection.disconnect()
        }
    }
    throw IOException("Too many release download redirects")
}

private fun parseApk(asset: JSONObject): Pair<Long, String>? {
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
    return versionCode to apkUrl
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
