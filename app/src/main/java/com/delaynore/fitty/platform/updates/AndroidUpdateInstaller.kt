package com.delaynore.fitty.platform.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.delaynore.fitty.data.updates.InstallSession
import com.delaynore.fitty.data.updates.UpdateAttempt
import com.delaynore.fitty.data.updates.UpdateAttemptStore
import com.delaynore.fitty.data.updates.UpdateSessionManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

internal interface UpdateInstaller : UpdateSessionManager {
    fun permissionIntent(): Intent
    fun takeConfirmation(sessionId: Int, requestId: Long): Intent?
}

internal class AndroidUpdateInstaller(
    private val context: Context,
    private val attempts: UpdateAttemptStore,
) : UpdateInstaller {
    private val packageInstaller get() = context.packageManager.packageInstaller
    override val sessionUpdates: Flow<Unit> = updates.asSharedFlow()

    override fun canRequestInstall() = context.packageManager.canRequestPackageInstalls()

    override suspend fun commitInstall(apk: File, expectedVersionCode: Long): InstallSession? =
        withContext(Dispatchers.IO) {
            if (!apk.isFile) return@withContext null
            val previous = attempts.read()
            if (previous != null) abandonSession(previous.sessionId)
            ensureActive()
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            var sessionId: Int? = null
            var submitted: InstallSession? = null
            try {
                val id = packageInstaller.createSession(params)
                sessionId = id
                packageInstaller.openSession(id).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("fitty-update.apk", 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    ensureActive()
                    val requestId = nextRequestId.incrementAndGet()
                    attempts.write(UpdateAttempt(id, expectedVersionCode, requestId = requestId))
                    val callback = Intent(context, UpdateInstallReceiver::class.java)
                        .putExtra(UpdateInstallReceiver.EXTRA_REQUEST_ID, requestId)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pending = PendingIntent.getBroadcast(context, id, callback, flags)
                    ensureActive()
                    session.commit(pending.intentSender)
                    // The receiver needs the attempt even if the caller is cancelled on return from IO.
                    submitted = InstallSession(id, requestId)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            } finally {
                if (submitted == null) {
                    sessionId?.let { runCatching { packageInstaller.abandonSession(it) } }
                    attempts.clear()
                }
            }
            submitted
        }

    override suspend fun abandonSession(sessionId: Int) = withContext(Dispatchers.IO) {
        runCatching { packageInstaller.abandonSession(sessionId) }
        Unit
    }

    override fun hasSession(sessionId: Int) = packageInstaller.getSessionInfo(sessionId) != null

    override fun hasConfirmation(sessionId: Int, requestId: Long) = confirmations.containsKey(Key(sessionId, requestId))

    override fun permissionIntent() =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))

    override fun takeConfirmation(sessionId: Int, requestId: Long): Intent? = confirmations.remove(Key(sessionId, requestId))

    companion object {
        private data class Key(val sessionId: Int, val requestId: Long)
        private val nextRequestId = AtomicLong()
        private val confirmations = ConcurrentHashMap<Key, Intent>()
        private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        internal fun rememberConfirmation(sessionId: Int, requestId: Long, intent: Intent) {
            confirmations[Key(sessionId, requestId)] = intent
        }

        internal fun notifySessionUpdated() {
            updates.tryEmit(Unit)
        }
    }
}
