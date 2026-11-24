package com.delaynore.fitty.data.updates

import java.io.File
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateApkDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val cache by lazy { UpdateApkCache(folder.root) { _, _ -> true } }
    private val apkUrl = "https://github.com/delaynore/fitty/releases/download/v1.0-build-8/fitty-8.apk"

    @Test
    fun progressUsesTheReleaseSizeWhenHttpOmitsTheLength() = runBlocking {
        val body = ByteArray(DEFAULT_BUFFER_SIZE * 2)
        val openConnection = { url: URL -> FakeApkResponse(url, body = body) }
        val progress = mutableListOf<Int>()

        val result = transfer(openConnection, FittyUpdate("1.0", 8, apkUrl, body.size.toLong()), progress::add)

        assertEquals(listOf(0, 50, 100), progress)
        assertTrue(result is UpdateDownloadResult.Downloaded)
    }

    @Test
    fun progressUsesTheHttpLengthAheadOfTheReleaseSize() = runBlocking {
        val body = ByteArray(DEFAULT_BUFFER_SIZE * 2)
        val openConnection = { url: URL -> FakeApkResponse(url, body = body, length = body.size.toLong()) }
        val progress = mutableListOf<Int>()

        val result = transfer(openConnection, FittyUpdate("1.0", 8, apkUrl, 1), progress::add)

        assertEquals(listOf(0, 50, 100), progress)
        assertTrue(result is UpdateDownloadResult.Downloaded)
    }

    @Test
    fun distinguishesNetworkDeliveryAndCacheWriteFailures() = runBlocking {
        val update = FittyUpdate("1.0", 8, apkUrl, 4)
        val offline = { url: URL -> throw UnknownHostException(url.host) }
        val missingApk = { url: URL -> FakeApkResponse(url, status = 404) }
        val incomplete = { url: URL -> FakeApkResponse(url, body = ByteArray(2)) }
        val notADirectory = folder.newFile()
        val cannotWrite = UpdateApkCache(notADirectory) { _, _ -> error("The cache file was not created") }

        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Network), transfer(offline, update))
        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Delivery), transfer(missingApk, update))
        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.Delivery), transfer(incomplete, update))
        assertEquals(
            UpdateDownloadResult.Failed(UpdateDownloadFailure.LocalWrite),
            cannotWrite.download(update, 7) { error("The cache file was not created") },
        )
    }

    @Test
    fun prunesOldApksOnceAndPreservesTheApkAwaitingPermission() = runBlocking {
        val cache = folder.newFolder()
        val directory = File(cache, "updates").apply { mkdir() }
        val abandoned = File.createTempFile("fitty-update-", ".apk", directory)
        val waiting = File.createTempFile("fitty-update-", ".apk", directory)
        val unrelated = File(directory, "other.apk").apply { writeText("Unrelated file") }
        val updates = UpdateApkCache(cache) { _, _ -> error("Pruning does not parse APKs") }

        updates.pruneAbandonedDownloads(waiting)

        assertFalse(abandoned.exists())
        assertTrue(waiting.isFile)
        assertTrue(unrelated.isFile)
        updates.pruneAbandonedDownloads(null)
        assertTrue(waiting.isFile)
    }

    @Test
    fun cancellingADownloadRemovesItsPartialApk() = runBlocking {
        val started = CompletableDeferred<File>()
        val updates = UpdateApkCache(folder.root) { _, _ -> error("A cancelled download cannot be parsed") }
        val download = async {
            updates.download(FittyUpdate("1.0", 8, apkUrl), 7) { destination ->
                destination.writeText("Incomplete download")
                started.complete(destination)
                awaitCancellation()
            }
        }
        val partial = withTimeout(5_000) { started.await() }

        download.cancelAndJoin()

        assertFalse(partial.exists())
    }

    @Test
    fun deletingThePreviousDownloadKeepsTheRetryAvailableForInstallation() = runBlocking {
        val update = FittyUpdate("1.0", 8, apkUrl)
        val openConnection = { url: URL -> FakeApkResponse(url, body = ByteArray(4)) }
        val previous = transfer(openConnection, update) as UpdateDownloadResult.Downloaded
        val retry = transfer(openConnection, update) as UpdateDownloadResult.Downloaded

        assertTrue(previous.apk.delete())
        assertTrue(retry.apk.isFile)
    }

    private suspend fun transfer(
        openConnection: (URL) -> HttpsURLConnection,
        update: FittyUpdate,
        onProgress: (Int) -> Unit = {},
    ): UpdateDownloadResult {
        return cache.download(update, 7) { destination ->
            downloadReleaseApk(update, destination, onProgress, openConnection)
        }
    }
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
