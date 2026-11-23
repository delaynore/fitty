package com.delaynore.fitty

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.delaynore.fitty.data.updates.UpdateService
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class FittyTestRunner : AndroidJUnitRunner() {
    private lateinit var application: FittyTestApplication

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        application = super.newApplication(cl, FittyTestApplication::class.java.name, context) as FittyTestApplication
        return application
    }

    override fun finish(resultCode: Int, results: Bundle) {
        application.deleteDatabase()
        super.finish(resultCode, results)
    }
}

class FittyTestApplication : FittyApplication() {
    override val databaseName = "fitty-test-${UUID.randomUUID()}.db"

    private val workoutTime = MutableStateFlow<Long?>(null)
    var workoutTimeMillis: Long?
        get() = workoutTime.value
        set(value) { workoutTime.value = value }

    @Volatile
    internal var setTimerCueCount: AtomicInteger? = null

    @Volatile
    internal var updateServiceOverride: UpdateService? = null

    override val updateService: UpdateService
        get() = updateServiceOverride ?: super.updateService

    override fun workoutTimerElapsedRealtime(): Long = workoutTimeMillis ?: super.workoutTimerElapsedRealtime()

    override suspend fun awaitSetTimerDeadline(deadlineMillis: Long) {
        if (workoutTimeMillis == null) return super.awaitSetTimerDeadline(deadlineMillis)
        workoutTime.first { it != null && it >= deadlineMillis }
    }

    override suspend fun playSetTimerSignal() {
        val count = setTimerCueCount
        if (count == null) super.playSetTimerSignal() else count.incrementAndGet()
    }

    fun resetDatabase() = deleteDatabase()
}
