package com.delaynore.fitty

import android.app.Application
import android.os.SystemClock
import androidx.room.Room
import com.delaynore.fitty.data.local.FittyDatabase
import com.delaynore.fitty.data.updates.GitHubUpdateService
import com.delaynore.fitty.data.updates.UpdateService

open class FittyApplication : Application() {
    protected open val databaseName = "fitty.db"

    private var room: FittyDatabase? = null

    internal val database: FittyDatabase
        get() = room ?: Room.databaseBuilder(this, FittyDatabase::class.java, databaseName).build().also { room = it }

    internal open fun restTimerElapsedRealtime(): Long = SystemClock.elapsedRealtime()

    internal open val updateService: UpdateService by lazy { GitHubUpdateService(this) }

    internal fun deleteDatabase() {
        room?.close()
        room = null
        deleteDatabase(databaseName)
    }
}
