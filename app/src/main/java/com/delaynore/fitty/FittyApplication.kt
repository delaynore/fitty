package com.delaynore.fitty

import android.app.Application
import androidx.room.Room
import com.delaynore.fitty.data.local.FittyDatabase

open class FittyApplication : Application() {
    protected open val databaseName = "fitty.db"

    private val room = lazy {
        Room.databaseBuilder(this, FittyDatabase::class.java, databaseName).build()
    }

    internal val database by room

    internal fun deleteDatabase() {
        if (room.isInitialized()) database.close()
        deleteDatabase(databaseName)
    }
}
