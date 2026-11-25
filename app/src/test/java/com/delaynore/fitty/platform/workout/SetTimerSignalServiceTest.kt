package com.delaynore.fitty.platform.workout

import android.media.ToneGenerator
import androidx.test.core.app.ApplicationProvider
import com.delaynore.fitty.FittyApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = SignalServiceTestApplication::class, shadows = [ShadowStartedToneGenerator::class])
class SetTimerSignalServiceTest {
    @Test
    fun playSetTimerCuePropagatesCancellationAndReleasesToneGenerator() = runTest {
        ShadowStartedToneGenerator.reset()
        val context = ApplicationProvider.getApplicationContext<FittyApplication>()
        var caught: Throwable? = null
        val job = async {
            try {
                playSetTimerCue(context)
            } catch (failure: Throwable) {
                caught = failure
                throw failure
            }
        }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, ShadowStartedToneGenerator.startedCount)

        job.cancel()
        runCurrent()

        assertTrue(caught is CancellationException)
        assertEquals(1, ShadowStartedToneGenerator.releasedCount)
    }

    @Test
    fun cancellingAnOlderSignalInCollectLatestDoesNotStopServiceForTheNewSignal() {
        val application = ApplicationProvider.getApplicationContext<SignalServiceTestApplication>()
        val firstCueStarted = CompletableDeferred<Unit>()
        val firstCueCancelled = CompletableDeferred<Unit>()
        val secondCuePlayed = CompletableDeferred<Unit>()
        application.onPlaySignal = { callIndex ->
            if (callIndex == 1) {
                firstCueStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstCueCancelled.complete(Unit)
                }
            } else {
                secondCuePlayed.complete(Unit)
            }
        }

        val controller = Robolectric.buildService(SetTimerSignalService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(application.setTimerSignals.schedule("first", workoutId = 1, deadlineMillis = 10_000L))
            service.onStartCommand(null, 0, 1)
            application.nowMillis = 10_000L
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(firstCueStarted.isCompleted)

            assertTrue(application.setTimerSignals.schedule("second", workoutId = 1, deadlineMillis = 20_000L))
            service.onStartCommand(null, 0, 2)
            shadowOf(android.os.Looper.getMainLooper()).idle()

            assertTrue(firstCueCancelled.isCompleted)
            assertFalse(shadowOf(service).isStoppedBySelf)
            assertEquals("second", application.setTimerSignals.pending.value?.token)

            application.nowMillis = 20_000L
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(secondCuePlayed.isCompleted)
            assertNull(application.setTimerSignals.pending.value)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }
}

@Implements(ToneGenerator::class)
class ShadowStartedToneGenerator {
    @Implementation
    fun __constructor__(streamType: Int, volume: Int) = Unit

    @Implementation
    fun startTone(toneType: Int, durationMs: Int): Boolean {
        startedCount++
        return true
    }

    @Implementation
    fun release() {
        releasedCount++
    }

    companion object {
        var startedCount = 0
            private set
        var releasedCount = 0
            private set

        fun reset() {
            startedCount = 0
            releasedCount = 0
        }
    }
}

class SignalServiceTestApplication : FittyApplication() {
    override val databaseName = "signal-service-test.db"
    private val clock = MutableStateFlow(0L)
    private var playCount = 0
    var onPlaySignal: suspend (Int) -> Unit = {}

    var nowMillis: Long
        get() = clock.value
        set(value) { clock.value = value }

    override fun workoutTimerElapsedRealtime() = nowMillis

    override suspend fun awaitSetTimerDeadline(deadlineMillis: Long) {
        clock.first { it >= deadlineMillis }
    }

    override suspend fun playSetTimerSignal() {
        onPlaySignal(++playCount)
    }
}
