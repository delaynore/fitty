package com.delaynore.fitty.platform.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.data.updates.UpdateAttempt
import com.delaynore.fitty.data.updates.UpdateAttemptStore
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidUpdateInstallerTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun cancellationBeforeCommitAbandonsTheStagedSessionAndClearsTheAttempt() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stored = SharedPreferencesUpdateAttemptStore(context, "installer-test")
        var stagedAttempt: UpdateAttempt? = null
        lateinit var install: Job
        val attempts = object : UpdateAttemptStore by stored {
            override fun write(attempt: UpdateAttempt) {
                stored.write(attempt)
                stagedAttempt = attempt
                install.cancel()
            }
        }
        val installer = AndroidUpdateInstaller(context, attempts)
        val apk = files.newFile("update.apk").apply { writeText("test APK contents") }

        install = launch(start = CoroutineStart.LAZY) { installer.commitInstall(apk, expectedVersionCode = 2L) }
        install.start()
        install.join()

        assertTrue(install.isCancelled)
        assertFalse(installer.hasSession(requireNotNull(stagedAttempt).sessionId))
        assertNull(stored.read())
    }

    @Test
    fun cancellationOnReturnFromIoKeepsTheCommittedAttemptForTheReceiver() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val attempts = SharedPreferencesUpdateAttemptStore(context, "installer-test")
        val installer = AndroidUpdateInstaller(context, attempts)
        val apk = files.newFile("update.apk").apply { writeText("test APK contents") }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val caller = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                context[Job]?.cancel()
                testDispatcher.dispatch(context, block)
            }
        }

        // Start inline so the first dispatch to caller happens after the installer returns from IO.
        val install = launch(caller, start = CoroutineStart.UNDISPATCHED) {
            installer.commitInstall(apk, expectedVersionCode = 2L)
        }
        install.join()

        assertTrue(install.isCancelled)
        val attempt = requireNotNull(attempts.read())
        assertEquals(2L, attempt.expectedVersionCode)
        assertTrue(installer.hasSession(attempt.sessionId))
    }
}
