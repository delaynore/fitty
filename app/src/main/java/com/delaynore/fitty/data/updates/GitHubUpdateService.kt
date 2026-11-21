package com.delaynore.fitty.data.updates

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/delaynore/fitty/releases/latest"
private const val RELEASE_DOWNLOAD_PATH = "/delaynore/fitty/releases/download/"
private const val REQUEST_TIMEOUT_MILLIS = 10_000
private val apkName = Regex("""fitty-([1-9][0-9]*)\.apk""")

internal data class GitHubReleaseResponse(val status: Int, val body: String = "")

internal class GitHubUpdateService(
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
            ?: release.getString("tag_name")
        return UpdateCheckResult.Available(FittyUpdate(versionName, versionCode, apkUrl))
    } catch (_: JSONException) {
        return UpdateCheckResult.Failed(UpdateCheckFailure.GitHub)
    }
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
