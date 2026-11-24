package com.delaynore.fitty

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.delaynore.fitty.data.updates.UpdateService
import java.util.UUID

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

    @Volatile
    internal var updateServiceOverride: UpdateService? = null

    override val updateService: UpdateService
        get() = updateServiceOverride ?: super.updateService

    fun resetDatabase() = deleteDatabase()
}
