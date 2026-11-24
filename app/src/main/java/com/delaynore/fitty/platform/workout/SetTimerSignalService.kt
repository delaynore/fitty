package com.delaynore.fitty.platform.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.delaynore.fitty.FittyApplication
import com.delaynore.fitty.MainActivity
import com.delaynore.fitty.R
import com.delaynore.fitty.domain.SetTimerSignal
import com.delaynore.fitty.domain.SetTimerSignalScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val NOTIFICATION_CHANNEL_ID = "set_timer"
private const val NOTIFICATION_ID = 1
private const val SIGNAL_MILLIS = 750L

internal class SetTimerSignals(private val context: Context) : SetTimerSignalScheduler {
    private val request = MutableStateFlow<SetTimerSignal?>(null)
    private val failureEvents = MutableSharedFlow<SetTimerSignal>(extraBufferCapacity = 1)
    val pending = request.asStateFlow()
    override val failures = failureEvents.asSharedFlow()

    override fun schedule(token: String, workoutId: Long, deadlineMillis: Long): Boolean {
        val signal = SetTimerSignal(token, workoutId, deadlineMillis)
        if (request.value == signal) return true
        request.value = signal
        return try {
            ContextCompat.startForegroundService(context, Intent(context, SetTimerSignalService::class.java))
            true
        } catch (failure: RuntimeException) {
            if (failure !is IllegalStateException && failure !is SecurityException) throw failure
            cancel(token)
            Log.w("SetTimerSignal", "Unable to start set timer signal service", failure)
            false
        }
    }

    override fun cancel(token: String) {
        val signal = request.value ?: return
        if (signal.token == token) request.compareAndSet(signal, null)
    }

    override fun cancelWorkout(workoutId: Long) {
        val signal = request.value ?: return
        if (signal.workoutId == workoutId) request.compareAndSet(signal, null)
    }

    fun reportFailure(signal: SetTimerSignal) {
        failureEvents.tryEmit(signal)
    }
}

internal class SetTimerSignalService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fitty get() = application as FittyApplication
    private var latestStartId = 0
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        try {
            showNotification()
        } catch (failure: RuntimeException) {
            if (failure !is IllegalStateException && failure !is SecurityException) throw failure
            Log.w("SetTimerSignal", "Unable to start foreground set timer service", failure)
            fitty.setTimerSignals.pending.value?.let { signal ->
                fitty.setTimerSignals.reportFailure(signal)
                fitty.setTimerSignals.cancel(signal.token)
            }
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            scope.launch {
                fitty.setTimerSignals.pending.collectLatest { signal ->
                    if (signal == null) {
                        stopSelfResult(latestStartId)
                    } else {
                        try {
                            signalAtDeadline(signal)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Throwable) {
                            Log.w("SetTimerSignal", "Unable to signal set timer deadline", failure)
                        } finally {
                            fitty.setTimerSignals.cancel(signal.token)
                            stopSelfResult(latestStartId)
                        }
                    }
                }
            }
        } else if (fitty.setTimerSignals.pending.value == null) {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun signalAtDeadline(signal: SetTimerSignal) {
        val wakeLock = try {
            getSystemService(PowerManager::class.java)?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:SetTimerSignal",
            )
        } catch (failure: RuntimeException) {
            Log.w("SetTimerSignal", "Unable to acquire wake lock", failure)
            null
        }
        val remainingMillis = (signal.deadlineMillis - fitty.workoutTimerElapsedRealtime()).coerceAtLeast(0L)
        try {
            wakeLock?.acquire(remainingMillis + SIGNAL_MILLIS + 1_000L)
        } catch (failure: RuntimeException) {
            Log.w("SetTimerSignal", "Unable to acquire wake lock", failure)
        }
        try {
            fitty.awaitSetTimerDeadline(signal.deadlineMillis)
            if (fitty.setTimerSignals.pending.value == signal) fitty.playSetTimerSignal()
        } finally {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (failure: RuntimeException) {
                Log.w("SetTimerSignal", "Unable to release wake lock", failure)
            }
        }
    }

    private fun showNotification() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Замер подхода", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_set_timer)
            .setContentTitle("Идёт замер подхода")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}

internal suspend fun playSetTimerCue(context: Context) {
    try {
        vibrateSetTimerCue(context)
    } catch (failure: RuntimeException) {
        Log.w("SetTimerSignal", "Unable to vibrate for set timer", failure)
    }
    val tone = try {
        ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
    } catch (failure: RuntimeException) {
        Log.w("SetTimerSignal", "Unable to play the set timer tone", failure)
        null
    }
    try {
        if (tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, SIGNAL_MILLIS.toInt()) == true) {
            delay(SIGNAL_MILLIS)
        } else if (tone != null) {
            Log.w("SetTimerSignal", "ToneGenerator rejected the set timer tone")
        }
    } catch (failure: RuntimeException) {
        Log.w("SetTimerSignal", "Unable to play the set timer tone", failure)
    } finally {
        try {
            tone?.release()
        } catch (failure: RuntimeException) {
            Log.w("SetTimerSignal", "Unable to release set timer tone", failure)
        }
    }
}

@Suppress("DEPRECATION")
private fun vibrateSetTimerCue(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }
    val effect = VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE)
    if (Build.VERSION.SDK_INT >= 33) {
        vibrator?.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    } else {
        vibrator?.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }
}
