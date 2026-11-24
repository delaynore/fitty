# Fitty Android development

## Run checks

Set up JDK 21 and the Android SDK as described below. From the repository root, run the JVM tests, build the app, and run Android lint.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Start a dedicated Android emulator and list connected devices to find its serial. Pass that serial to the test script. The tests use a separate Room database and temporarily toggle airplane mode.

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices
.\scripts\test-android.ps1 -Serial emulator-5556
```

The script prints the test log path under `build/reports/android-device/`. GitHub Actions runs build and lint on every pull request and runs the JVM tests before starting the Android 16 emulator for instrumented tests.

## Build and sign an APK

Install JDK 21 and Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools. Set `JAVA_HOME` and `ANDROID_HOME` to the JDK and SDK directories. The commands below use PowerShell from the repository root.

Create a signing key once, before the first installation. Keep the keystore and its password outside the repository. Back up the key so you can sign future updates with it.

```powershell
$keystore = "$env:USERPROFILE/fitty-release.p12"
& "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore "$keystore" -storetype PKCS12 -alias fitty -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Fitty'
```

For each release, set `fittyVersionCode` higher than the last distributed APK's code and set `fittyVersionName` to the version to display in Fitty. The example builds version `1.1`, code `5`. Sign it with the same key as the installed APK. The command uses the `fitty` alias from the key creation step. Enter the password when `apksigner` prompts for it.

```powershell
$keystore = "$env:USERPROFILE/fitty-release.p12"
$buildTools = "$env:ANDROID_HOME/build-tools/36.0.0"
$versionCode = 5
$versionName = '1.1'
$releaseDir = 'app/build/outputs/apk/release'
$apk = "$releaseDir/fitty-$versionCode.apk"
.\gradlew.bat :app:assembleRelease "-PfittyVersionCode=$versionCode" "-PfittyVersionName=$versionName"
if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
& "$buildTools/apksigner.bat" sign --ks "$keystore" --ks-key-alias fitty --out "$apk" "$releaseDir/app-release-unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$buildTools/apksigner.bat" verify --print-certs "$apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$buildTools/aapt.exe" dump badging "$apk" | Select-String '^package:'
```

For an update, compare the `Signer #1 certificate SHA-256 digest` with the previous signed APK. Check that `aapt` reports `com.delaynore.fitty`, the new `versionCode`, and the chosen `versionName`. [Android signing and verification](https://developer.android.com/tools/apksigner)

## Publish on GitHub

1. In the public `delaynore/fitty` repository, open **Releases** and choose **Draft a new release**. Tag the commit used to build the APK. Use a unique tag such as `v1.1-build-5` so a later build can reuse the Android version name.
2. Set the release title to the exact `versionName` from the APK, such as `1.1`.
3. Attach one signed APK named `fitty-<versionCode>.apk`. Use the code reported by `aapt`.
4. Leave **This is a pre-release** clear and select **Set as latest release**. Click **Publish release**. Fitty reads that release to offer updates. [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

## Install or update Fitty

For the first installation or to install manually, copy the signed `fitty-<versionCode>.apk` to the phone and open it in the Files app. If Android asks, allow that app to install APKs. Tap Install or Update. Open Fitty from its launcher icon.

To update from Fitty, open Settings, tap **Проверить обновления**, then **Обновить**. If Android opens settings for installs from Fitty, allow them and return to Fitty. Confirm the update in Android's installer.

With USB debugging enabled, you can also install the APK from the computer. Use `adb devices` to find the device serial and pass it in place of `DEVICE_SERIAL`. The `-r` flag replaces the installed app and keeps its data. [Android Debug Bridge](https://developer.android.com/tools/adb)

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s DEVICE_SERIAL install -r "$apk"
```

To verify an update, keep the previous version installed and save at least two training programs. Select one for Home, log a set in a completed workout, and leave another workout in progress with a logged set. Install the new APK over the existing app. With airplane mode on and Wi-Fi off, open Fitty from its icon and check the program list, the selected program on Home, the in-progress workout and its logged set, and the completed workout in History. If the Room schema changed, create the records in the previous version so the update exercises the migration.
