package com.delaynore.fitty

import android.app.Application
import androidx.room.Room
import com.delaynore.fitty.data.local.FittyDatabase

open class FittyApplication : Application() {
    protected open val databaseName = "fitty.db"

    private var room: FittyDatabase? = null

    internal val database: FittyDatabase
        get() = room ?: Room.databaseBuilder(this, FittyDatabase::class.java, databaseName).build().also { room = it }

    internal fun deleteDatabase() {
        room?.close()
        room = null
        deleteDatabase(databaseName)
    }
}
