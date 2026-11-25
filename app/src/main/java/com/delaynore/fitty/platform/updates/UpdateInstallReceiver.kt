package com.delaynore.fitty.platform.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.delaynore.fitty.FittyApplication
import com.delaynore.fitty.data.updates.InstallOutcome

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return
        val databaseName = (context.applicationContext as? FittyApplication)?.databaseName ?: FittyApplication.DATABASE_NAME
        val store = SharedPreferencesUpdateAttemptStore(context, databaseName)
        val attempt = store.read()?.takeIf { it.sessionId == sessionId && it.outcome == null } ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = intent.confirmationIntent() ?: return
                val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, sessionId.toLong())
                AndroidUpdateInstaller.rememberConfirmation(sessionId, requestId, confirmation)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                store.write(attempt.copy(outcome = InstallOutcome.Success))
                AndroidUpdateInstaller.notifySessionUpdated()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                store.write(attempt.copy(outcome = InstallOutcome.Aborted))
                AndroidUpdateInstaller.notifySessionUpdated()
            }
            else -> {
                store.write(attempt.copy(outcome = InstallOutcome.Failure))
                AndroidUpdateInstaller.notifySessionUpdated()
            }
        }
    }

    private fun Intent.confirmationIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_INTENT)
    }

    companion object { internal const val EXTRA_REQUEST_ID = "requestId" }
}
