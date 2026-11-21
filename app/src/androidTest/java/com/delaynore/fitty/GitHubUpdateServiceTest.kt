package com.delaynore.fitty

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.GitHubReleaseResponse
import com.delaynore.fitty.data.updates.GitHubUpdateService
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubUpdateServiceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val apkUrl = "https://github.com/delaynore/fitty/releases/download/v1.0-build-8/fitty-8.apk"
    private val stableRelease = """
        {
            "tag_name": "v1.0-build-8",
            "name": "1.0",
            "draft": false,
            "prerelease": false,
            "assets": [
                {
                    "name": "fitty-8.apk",
                    "state": "uploaded",
                    "browser_download_url": "$apkUrl"
                },
                {
                    "name": "notes.txt",
                    "state": "uploaded",
                    "browser_download_url": "https://github.com/delaynore/fitty/releases/download/v1.0-build-8/notes.txt"
                }
            ]
        }
    """.trimIndent()

    @Test
    fun offersTheApkFromTheStableReleaseWithAHigherBuildCode() = runBlocking {
        val updates = GitHubUpdateService(context) { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.Available(FittyUpdate("1.0", 8, apkUrl)), updates.checkForUpdate(7))
    }

    @Test
    fun equalAndOlderReleaseBuildsLeaveTheInstalledBuildCurrent() = runBlocking {
        val updates = GitHubUpdateService(context) { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.UpToDate, updates.checkForUpdate(8))
        assertEquals(UpdateCheckResult.UpToDate, updates.checkForUpdate(9))
    }

    @Test
    fun ignoresDraftsAndPrereleasesEvenWhenTheyContainANewerApk() = runBlocking {
        for (flag in listOf("draft", "prerelease")) {
            val release = stableRelease.replace("\"$flag\": false", "\"$flag\": true")
            val updates = GitHubUpdateService(context) { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.NoStableRelease, updates.checkForUpdate(1))
        }
    }

    @Test
    fun missingLatestReleaseMeansThereIsNoStableRelease() = runBlocking {
        val updates = GitHubUpdateService(context) { GitHubReleaseResponse(404, """{"message":"Not Found"}""") }

        assertEquals(UpdateCheckResult.NoStableRelease, updates.checkForUpdate(1))
    }

    @Test
    fun aStableReleaseWithoutASuitableApkFailsTheCheck() = runBlocking {
        val wrongFilename = stableRelease.replace("fitty-8.apk", "fitty-1.0.apk")
        val externalUrl = stableRelease.replace(apkUrl, "https://example.com/fitty-8.apk")
        val unfinishedUpload = stableRelease.replace("\"state\": \"uploaded\"", "\"state\": \"starter\"")
        for (release in listOf(wrongFilename, externalUrl, unfinishedUpload)) {
            val updates = GitHubUpdateService(context) { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.MissingApk), updates.checkForUpdate(1))
        }
    }

    @Test
    fun githubErrorsAndUnusableResponsesFailTheCheck() = runBlocking {
        val rateLimited = GitHubReleaseResponse(403, """{"message":"API rate limit exceeded"}""")
        val malformedRelease = GitHubReleaseResponse(200, "{")
        for (response in listOf(rateLimited, malformedRelease)) {
            val updates = GitHubUpdateService(context) { response }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.GitHub), updates.checkForUpdate(1))
        }
    }

    @Test
    fun unreachableNetworkFailsTheCheckWithANetworkError() = runBlocking {
        val updates = GitHubUpdateService(context) { throw UnknownHostException("api.github.com") }

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Network), updates.checkForUpdate(1))
    }

    @Test
    fun theReleaseTitleMustProvideTheVersionName() = runBlocking {
        val unnamedRelease = stableRelease.replace("\"name\": \"1.0\"", "\"name\": \"\"")
        val updates = GitHubUpdateService(context) { GitHubReleaseResponse(200, unnamedRelease) }

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.GitHub), updates.checkForUpdate(7))
    }

    @Test
    fun aDownloadThatDoesNotContainAnApkCannotReachTheInstaller() = runBlocking {
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            destination.writeText("A broken APK")
        })
        val update = FittyUpdate("1.0", 8, apkUrl)

        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), updates.download(update, 7))
    }

    @Test
    fun anApkWithADifferentVersionNameOrCodeFromTheReleaseCannotReachTheInstaller() = runBlocking {
        val apk = File(context.applicationInfo.sourceDir)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(info)
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            apk.copyTo(destination, overwrite = true)
        })

        val mismatchedUpdates = listOf(
            FittyUpdate(info.versionName.orEmpty(), code + 1, apkUrl),
            FittyUpdate("${info.versionName}-other", code, apkUrl),
        )
        for (update in mismatchedUpdates) {
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), updates.download(update, code - 1))
        }
    }

    @Test
    fun removingAnEarlierDownloadsApkLeavesTheRetryAvailableForInstallation() = runBlocking {
        val installedApk = File(context.applicationInfo.sourceDir)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(info)
        val update = FittyUpdate(info.versionName.orEmpty(), code, apkUrl)
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            installedApk.copyTo(destination, overwrite = true)
        })

        val previous = updates.download(update, code - 1) as UpdateDownloadResult.Downloaded
        try {
            val retry = updates.download(update, code - 1) as UpdateDownloadResult.Downloaded
            try {
                assertTrue(previous.apk.delete())
                assertTrue(retry.apk.isFile)
            } finally {
                retry.apk.delete()
            }
        } finally {
            previous.apk.delete()
        }
    }

    @Test
    fun cancellingADownloadRemovesItsPartialApk() = runBlocking {
        val started = CompletableDeferred<File>()
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            destination.writeText("Incomplete download")
            started.complete(destination)
            awaitCancellation()
        })
        val download = async { updates.download(FittyUpdate("1.0", 8, apkUrl), 7) }
        val partial = withTimeout(5_000) { started.await() }

        download.cancelAndJoin()

        assertFalse(partial.exists())
    }
}
