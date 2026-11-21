package com.delaynore.fitty.data.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

internal class AndroidUpdateInstaller(private val context: Context) {
    fun canRequestInstall() = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(launch: (Intent) -> Unit): Boolean = launchActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)),
        launch,
    )

    @Suppress("DEPRECATION")
    fun openInstaller(apk: File, launch: (Intent) -> Unit): Boolean {
        if (!apk.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return launchActivity(install, launch)
    }

    private fun launchActivity(intent: Intent, launch: (Intent) -> Unit): Boolean = try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
