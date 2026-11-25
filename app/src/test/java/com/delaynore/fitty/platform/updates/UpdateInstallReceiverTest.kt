package com.delaynore.fitty.platform.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.FittyApplication
import com.delaynore.fitty.data.updates.InstallOutcome
import com.delaynore.fitty.data.updates.UpdateAttempt
import com.delaynore.fitty.data.updates.UpdateAttemptPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateInstallReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = SharedPreferencesUpdateAttemptStore(context, FittyApplication.DATABASE_NAME)
    private val receiver = UpdateInstallReceiver()

    @After
    fun cleanUp() {
        store.clear()
    }

    @Test
    fun terminalStatusesPersistOutcomeAndIgnoreForeignOrDuplicateBroadcasts() {
        val attempt = UpdateAttempt(7, 2, requestId = 11, phase = UpdateAttemptPhase.Installing)
        store.write(attempt)

        receiver.onReceive(
            context,
            Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 99).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS),
        )
        assertNull(store.read()?.outcome)

        receiver.onReceive(
            context,
            Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 7).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_ABORTED),
        )
        assertEquals(InstallOutcome.Aborted, store.read()?.outcome)

        receiver.onReceive(
            context,
            Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID, 7).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS),
        )
        assertEquals(InstallOutcome.Aborted, store.read()?.outcome)
    }

    @Test
    fun pendingUserActionStoresConfirmationForMatchingSession() {
        val attempt = UpdateAttempt(8, 2, requestId = 15)
        store.write(attempt)
        val confirm = Intent("confirm-action")
        val installer = AndroidUpdateInstaller(context, store)

        receiver.onReceive(
            context,
            Intent()
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, 8)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
                .putExtra(UpdateInstallReceiver.EXTRA_REQUEST_ID, 15L)
                .putExtra(Intent.EXTRA_INTENT, confirm),
        )

        assertEquals(confirm, installer.takeConfirmation(8, 15L))
        assertNull(store.read()?.outcome)
    }
}
