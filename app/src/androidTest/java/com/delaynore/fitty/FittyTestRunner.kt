package com.delaynore.fitty

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
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
}
