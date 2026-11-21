package com.delaynore.fitty

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.GitHubReleaseResponse
import com.delaynore.fitty.data.updates.GitHubUpdateService
import com.delaynore.fitty.data.updates.UpdateCheckFailure
import com.delaynore.fitty.data.updates.UpdateCheckResult
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubUpdateServiceTest {
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
        val updates = GitHubUpdateService { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.Available(FittyUpdate("1.0", 8, apkUrl)), updates.checkForUpdate(7))
    }

    @Test
    fun equalAndOlderReleaseBuildsLeaveTheInstalledBuildCurrent() = runBlocking {
        val updates = GitHubUpdateService { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.UpToDate, updates.checkForUpdate(8))
        assertEquals(UpdateCheckResult.UpToDate, updates.checkForUpdate(9))
    }

    @Test
    fun ignoresDraftsAndPrereleasesEvenWhenTheyContainANewerApk() = runBlocking {
        for (flag in listOf("draft", "prerelease")) {
            val release = stableRelease.replace("\"$flag\": false", "\"$flag\": true")
            val updates = GitHubUpdateService { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.NoStableRelease, updates.checkForUpdate(1))
        }
    }

    @Test
    fun missingLatestReleaseMeansThereIsNoStableRelease() = runBlocking {
        val updates = GitHubUpdateService { GitHubReleaseResponse(404, """{"message":"Not Found"}""") }

        assertEquals(UpdateCheckResult.NoStableRelease, updates.checkForUpdate(1))
    }

    @Test
    fun aStableReleaseWithoutASuitableApkFailsTheCheck() = runBlocking {
        val wrongFilename = stableRelease.replace("fitty-8.apk", "fitty-1.0.apk")
        val externalUrl = stableRelease.replace(apkUrl, "https://example.com/fitty-8.apk")
        val unfinishedUpload = stableRelease.replace("\"state\": \"uploaded\"", "\"state\": \"starter\"")
        for (release in listOf(wrongFilename, externalUrl, unfinishedUpload)) {
            val updates = GitHubUpdateService { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.MissingApk), updates.checkForUpdate(1))
        }
    }

    @Test
    fun githubErrorsAndUnusableResponsesFailTheCheck() = runBlocking {
        val rateLimited = GitHubReleaseResponse(403, """{"message":"API rate limit exceeded"}""")
        val malformedRelease = GitHubReleaseResponse(200, "{")
        for (response in listOf(rateLimited, malformedRelease)) {
            val updates = GitHubUpdateService { response }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.GitHub), updates.checkForUpdate(1))
        }
    }

    @Test
    fun unreachableNetworkFailsTheCheckWithANetworkError() = runBlocking {
        val updates = GitHubUpdateService { throw UnknownHostException("api.github.com") }

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Network), updates.checkForUpdate(1))
    }
}
