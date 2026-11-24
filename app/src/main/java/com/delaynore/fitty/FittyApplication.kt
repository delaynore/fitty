package com.delaynore.fitty

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.room.Room
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.updates.GitHubUpdateService
import com.delaynore.fitty.data.updates.UpdateService
import com.delaynore.fitty.data.updates.SharedPreferencesUpdateAttemptStore
import com.delaynore.fitty.platform.updates.AndroidUpdateInstaller
import com.delaynore.fitty.ui.workout.SetTimerSignals
import com.delaynore.fitty.ui.workout.playSetTimerCue
import kotlinx.coroutines.delay

open class FittyApplication : Application() {
    internal open val databaseName = DATABASE_NAME

    private var container: AppContainer? = null

    internal fun appContainer(restoredState: Bundle?): AppContainer =
        container ?: AppContainer(this, restoredState).also { container = it }

    private var room: FittyDatabase? = null

    internal val database: FittyDatabase
        get() = room ?: Room.databaseBuilder(this, FittyDatabase::class.java, databaseName).build().also { room = it }

    internal open fun workoutTimerElapsedRealtime(): Long = SystemClock.elapsedRealtime()

    internal val setTimerSignals by lazy { SetTimerSignals(this) }

    internal open suspend fun awaitSetTimerDeadline(deadlineMillis: Long) {
        var remainingMillis = deadlineMillis - workoutTimerElapsedRealtime()
        while (remainingMillis > 0L) {
            delay(remainingMillis)
            remainingMillis = deadlineMillis - workoutTimerElapsedRealtime()
        }
    }

    internal open suspend fun playSetTimerSignal() = playSetTimerCue(this)

    internal open val updateService: UpdateService by lazy { GitHubUpdateService(this) }
    internal val updateAttemptStore by lazy { SharedPreferencesUpdateAttemptStore(this, databaseName) }
    internal open val updateInstaller by lazy { AndroidUpdateInstaller(this, updateAttemptStore) }

    internal fun clearAppContainer() {
        container?.close()
        container = null
    }

    internal fun closeDatabase() {
        clearAppContainer()
        room?.close()
        room = null
    }

    internal fun deleteDatabase() {
        closeDatabase()
        SharedPreferencesUpdateAttemptStore.clear(this, databaseName)
        deleteDatabase(databaseName)
    }

    companion object { internal const val DATABASE_NAME = "fitty.db" }
}
