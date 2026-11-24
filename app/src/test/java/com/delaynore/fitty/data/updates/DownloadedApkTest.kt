package com.delaynore.fitty.data.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadedApkTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val update = FittyUpdate("1.4", 42, "https://github.com/delaynore/fitty/releases/download/test/fitty-42.apk")

    @Test
    fun rejectsCorruptedApk() = runBlocking {
        val updates = GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            destination.writeText("A broken APK")
        })
        assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), updates.download(update, 41))
    }

    @Test
    fun validatesTheArchiveAgainstBothReleaseVersionFields() = runBlocking {
        val updates = serviceDownloadingFixture()
        val valid = updates.download(update, 41) as UpdateDownloadResult.Downloaded
        assertTrue(valid.apk.isFile)
        valid.apk.delete()

        val mismatches = listOf(update.copy(versionCode = 43), update.copy(versionName = "1.5"))
        for (mismatch in mismatches) {
            assertEquals(UpdateDownloadResult.Failed(UpdateDownloadFailure.InvalidApk), updates.download(mismatch, 41))
        }
    }

    private fun serviceDownloadingFixture(): GitHubUpdateService {
        val fixture = folder.newFile("valid-fitty.apk")
        // Minimal APK linked with aapt2. It contains the Fitty package and version metadata.
        requireNotNull(javaClass.getResourceAsStream("/updates/valid-fitty.apk")).use { source ->
            fixture.outputStream().use { destination -> source.copyTo(destination) }
        }
        return GitHubUpdateService(context, fetchApk = { _, destination, _ ->
            fixture.copyTo(destination, overwrite = true)
        })
    }
}
