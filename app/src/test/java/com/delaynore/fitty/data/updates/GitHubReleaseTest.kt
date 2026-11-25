package com.delaynore.fitty.data.updates

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseTest {
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
    fun offersTheApkFromTheStableReleaseWithAHigherBuildCode() {
        val fetchRelease = { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.Available(FittyUpdate("1.0", 8, apkUrl)), checkLatestRelease(7, fetchRelease))
    }

    @Test
    fun includesTheApkSizeWhenTheReleaseProvidesIt() {
        val releaseWithSize = stableRelease.replace("\"name\": \"fitty-8.apk\",", "\"name\": \"fitty-8.apk\", \"size\": 16384,")
        val fetchRelease = { GitHubReleaseResponse(200, releaseWithSize) }

        assertEquals(UpdateCheckResult.Available(FittyUpdate("1.0", 8, apkUrl, 16_384)), checkLatestRelease(7, fetchRelease))
    }

    @Test
    fun equalAndOlderReleaseBuildsLeaveTheInstalledBuildCurrent() {
        val fetchRelease = { GitHubReleaseResponse(200, stableRelease) }

        assertEquals(UpdateCheckResult.UpToDate, checkLatestRelease(8, fetchRelease))
        assertEquals(UpdateCheckResult.UpToDate, checkLatestRelease(9, fetchRelease))
    }

    @Test
    fun ignoresDraftsAndPrereleasesEvenWhenTheyContainANewerApk() {
        for (flag in listOf("draft", "prerelease")) {
            val release = stableRelease.replace("\"$flag\": false", "\"$flag\": true")
            val fetchRelease = { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.NoStableRelease, checkLatestRelease(1, fetchRelease))
        }
    }

    @Test
    fun missingLatestReleaseMeansThereIsNoStableRelease() {
        val fetchRelease = { GitHubReleaseResponse(404, """{"message":"Not Found"}""") }

        assertEquals(UpdateCheckResult.NoStableRelease, checkLatestRelease(1, fetchRelease))
    }

    @Test
    fun aStableReleaseWithoutASuitableApkFailsTheCheck() {
        val wrongFilename = stableRelease.replace("fitty-8.apk", "fitty-1.0.apk")
        val externalUrl = stableRelease.replace(apkUrl, "https://example.com/fitty-8.apk")
        val unfinishedUpload = stableRelease.replace("\"state\": \"uploaded\"", "\"state\": \"starter\"")
        for (release in listOf(wrongFilename, externalUrl, unfinishedUpload)) {
            val fetchRelease = { GitHubReleaseResponse(200, release) }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.MissingApk), checkLatestRelease(1, fetchRelease))
        }
    }

    @Test
    fun multipleValidApksFailTheCheckInsteadOfChoosingOne() {
        val duplicate = stableRelease
            .replace("\"name\": \"notes.txt\"", "\"name\": \"fitty-9.apk\"")
            .replace(
                "https://github.com/delaynore/fitty/releases/download/v1.0-build-8/notes.txt",
                "https://github.com/delaynore/fitty/releases/download/v1.0-build-8/fitty-9.apk",
            )

        assertEquals(
            UpdateCheckResult.Failed(UpdateCheckFailure.MissingApk),
            checkLatestRelease(1) { GitHubReleaseResponse(200, duplicate) },
        )
    }

    @Test
    fun githubErrorsAndUnusableResponsesFailTheCheck() {
        val rateLimited = GitHubReleaseResponse(403, """{"message":"API rate limit exceeded"}""")
        val malformedRelease = GitHubReleaseResponse(200, "{")
        for (response in listOf(rateLimited, malformedRelease)) {
            val fetchRelease = { response }

            assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.GitHub), checkLatestRelease(1, fetchRelease))
        }
    }

    @Test
    fun unreachableNetworkFailsTheCheckWithANetworkError() {
        val fetchRelease = { throw UnknownHostException("api.github.com") }

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.Network), checkLatestRelease(1, fetchRelease))
    }

    @Test
    fun theReleaseTitleMustProvideTheVersionName() {
        val unnamedRelease = stableRelease.replace("\"name\": \"1.0\"", "\"name\": \"\"")
        val fetchRelease = { GitHubReleaseResponse(200, unnamedRelease) }

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.GitHub), checkLatestRelease(7, fetchRelease))
    }

    @Test
    fun prereleaseChannelPicksTheFirstPublishedPrereleaseWithPrereleaseApk() {
        val preUrl = "https://github.com/delaynore/fitty/releases/download/pre-build-105/fitty-prerelease-105.apk"
        val listBody = """
            [
                {
                    "tag_name": "v1.2.0-build-8",
                    "name": "1.2.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [{ "name": "fitty-108.apk", "state": "uploaded", "browser_download_url": "https://github.com/delaynore/fitty/releases/download/v1.2.0-build-8/fitty-108.apk" }]
                },
                {
                    "tag_name": "pre-draft",
                    "name": "1.2.0-draft",
                    "draft": true,
                    "prerelease": true,
                    "assets": [{ "name": "fitty-prerelease-109.apk", "state": "uploaded", "browser_download_url": "https://github.com/delaynore/fitty/releases/download/pre-draft/fitty-prerelease-109.apk" }]
                },
                {
                    "tag_name": "pre-build-105",
                    "name": "1.2.0-test.105",
                    "draft": false,
                    "prerelease": true,
                    "assets": [{ "name": "fitty-prerelease-105.apk", "state": "uploaded", "browser_download_url": "$preUrl" }]
                }
            ]
        """.trimIndent()

        assertEquals(
            UpdateCheckResult.Available(FittyUpdate("1.2.0-test.105", 105, preUrl)),
            checkLatestRelease(101, UpdateChannel.Prerelease) { GitHubReleaseResponse(200, listBody) },
        )
        assertEquals(
            UpdateCheckResult.NoStableRelease,
            checkLatestRelease(101, UpdateChannel.Prerelease) { GitHubReleaseResponse(200, "[]") },
        )
    }
}
