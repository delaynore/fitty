# Fitty settings and updates

## Settings

Add Settings («Настройки») as the third bottom tab after Home and History. The About section («О приложении») shows Fitty's name, the installed version and build number, and the Check for updates button («Проверить обновления»). For example, «Версия 1.1 · сборка 4».

Fitty checks for updates only when the lifter taps the button. It reads the latest stable GitHub Release and compares the APK's advertised Android version code with the installed code. If the release has a higher code, Fitty shows the available version and the Update button («Обновить»). If the installed build is current or there are no stable releases yet, Fitty says there is no update available. If the check fails or a published release is missing its APK, Fitty explains the problem and offers to retry. The workout workflow works offline.

## Install an update

1. The lifter taps Update in Settings. If a workout is in progress or edits are unsaved, Fitty shows a warning and lets the lifter continue or go back. The warning explains that saved programs and logged sets will remain and unsaved changes may be lost. If everything is saved and no workout is in progress, Fitty starts the download immediately.
2. Fitty downloads the APK, shows progress, and lets the lifter cancel. If the lifter leaves Settings, Fitty waits until they return before opening the installer. If the download fails, the lifter can retry.
3. Fitty hands the APK to Android's installer. If Android asks the lifter to allow installs from Fitty, they can grant permission and return to continue. The lifter confirms the update in the installer.
4. After installing the update, the lifter can open Fitty and see the new version in Settings. Fitty keeps the selected program, training programs, any in-progress workout and its logged sets, and workout history.

## Publish and verify

Build and sign `fitty-<versionCode>.apk` with the existing application ID and signing key. Increase the Android version code with every release and use the same code in the APK filename so Fitty can compare builds before downloading. Attach the signed APK to a stable GitHub Release. Keep the release version consistent with the version shown in the app.

Install the first build with Settings using the existing APK installation process. Verify the new flow with two signed builds: install the older one with training data, run the check and update from Settings, then verify the installed version and saved data. Follow the existing device checks for an APK update. [Android development](android-development.md#install-or-update-fitty) · [Update channel decision](adr/0001-distribute-app-updates-through-github-releases.md)
