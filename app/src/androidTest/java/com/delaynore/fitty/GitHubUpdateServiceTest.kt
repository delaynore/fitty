package com.delaynore.fitty

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delaynore.fitty.data.updates.FittyUpdate
import com.delaynore.fitty.data.updates.GitHubUpdateService
import com.delaynore.fitty.data.updates.UpdateDownloadFailure
import com.delaynore.fitty.data.updates.UpdateDownloadResult
import com.delaynore.fitty.data.updates.downloadReleaseApk
import java.io.File
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlin.io.path.createTempDirectory
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

    @Test
    fun aDownloadThatDoesNotContainAnApkCannotReachTheInstaller() = runBlocking {
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            destination.writeText("A broken APK")
        })
        val update = FittyUpdate("1.0", 8, apkUrl)

        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), updates.download(update, 7))
    }

    @Test
    fun progressUsesTheReleaseSizeWhenHttpOmitsTheLength() = runBlocking {
        val body = ByteArray(DEFAULT_BUFFER_SIZE * 2)
        val updates = releaseDownload { FakeApkResponse(it, body = body) }
        val progress = mutableListOf<Int>()

        val result = updates.download(FittyUpdate("1.0", 8, apkUrl, body.size.toLong()), 7, progress::add)

        assertEquals(listOf(0, 50, 100), progress)
        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), result)
    }

    @Test
    fun progressUsesTheHttpLengthAheadOfTheReleaseSize() = runBlocking {
        val body = ByteArray(DEFAULT_BUFFER_SIZE * 2)
        val updates = releaseDownload { FakeApkResponse(it, body = body, length = body.size.toLong()) }
        val progress = mutableListOf<Int>()

        val result = updates.download(FittyUpdate("1.0", 8, apkUrl, 1), 7, progress::add)

        assertEquals(listOf(0, 50, 100), progress)
        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), result)
    }

    @Test
    fun distinguishesNetworkDeliveryAndCacheWriteFailures() = runBlocking {
        val update = FittyUpdate("1.0", 8, apkUrl, 4)
        val offline = releaseDownload { throw UnknownHostException(it.host) }
        val missingApk = releaseDownload { FakeApkResponse(it, status = 404) }
        val incomplete = releaseDownload { FakeApkResponse(it, body = ByteArray(2)) }
        val notADirectory = File.createTempFile("fitty-test-cache-", ".tmp", context.cacheDir)
        val unwritableCache = object : ContextWrapper(context) {
            override fun getCacheDir() = notADirectory
        }
        val cannotWrite = GitHubUpdateService(unwritableCache, fetchApk = { _, _, _ -> error("APK file creation failed") })

        try {
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Network), offline.download(update, 7))
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Delivery), missingApk.download(update, 7))
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Delivery), incomplete.download(update, 7))
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.LocalWrite), cannotWrite.download(update, 7))
        } finally {
            notADirectory.delete()
        }
    }

    @Test
    fun aNewProcessPrunesOldApksAndKeepsTheApkAwaitingPermission() = runBlocking {
        val cache = createTempDirectory(context.cacheDir.toPath(), "fitty-test-cache-").toFile()
        val isolatedContext = object : ContextWrapper(context) {
            override fun getCacheDir() = cache
        }
        try {
            val directory = File(cache, "updates").apply { mkdir() }
            val abandoned = File.createTempFile("fitty-update-", ".apk", directory)
            val waiting = File.createTempFile("fitty-update-", ".apk", directory)
            val unrelated = File(directory, "other.apk").apply { writeText("Unrelated file") }
            val updates = GitHubUpdateService(isolatedContext)

            updates.pruneAbandonedDownloads(waiting)

            assertFalse(abandoned.exists())
            assertTrue(waiting.isFile)
            assertTrue(unrelated.isFile)
            updates.pruneAbandonedDownloads(null)
            assertTrue(waiting.isFile)
        } finally {
            cache.deleteRecursively()
        }
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

    private fun releaseDownload(openConnection: (URL) -> HttpsURLConnection) =
        GitHubUpdateService(context, fetchApk = { update, destination, onProgress ->
            downloadReleaseApk(update, destination, onProgress, openConnection)
        })
}

private class FakeApkResponse(
    url: URL,
    private val status: Int = 200,
    private val body: ByteArray = byteArrayOf(),
    private val length: Long = -1,
) : HttpsURLConnection(url) {
    override fun getResponseCode() = status
    override fun getInputStream() = body.inputStream()
    override fun getContentLengthLong() = length
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy() = false
    override fun getCipherSuite() = "fake"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates() = emptyArray<Certificate>()
}
