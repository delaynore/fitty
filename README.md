# Fitty

Fitty is a personal strength training log for Android. It stores training programs, an in-progress workout, and workout history on the device.

See the [MVP overview](docs/mvp.md) for the workout workflow and Android architecture.

## Run checks

Set up JDK 17 and the Android SDK as described below. From the repository root, build the app and run Android lint.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Run the instrumented tests on a dedicated Android emulator. Gradle runs them on connected devices, so keep only the test emulator connected. The tests use a separate Room database and temporarily toggle airplane mode.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Build and sign an APK

Install JDK 17 and Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools. Set `JAVA_HOME` and `ANDROID_HOME` to the JDK and SDK directories. The commands below use PowerShell from the repository root.

Create a signing key once, before the first installation. Keep the keystore and its password outside the repository. Back up the key so you can sign future updates with it.

```powershell
$keystore = "$env:USERPROFILE/fitty-release.p12"
& "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore "$keystore" -storetype PKCS12 -alias fitty -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Fitty'
```

For each release, set `fittyVersionCode` higher than the code of the last distributed APK. The example builds version code `3` for an update from `2`. Sign it with the same key as the installed APK. The command uses the `fitty` alias from the key creation step. Enter the password when `apksigner` prompts for it.

```powershell
$keystore = "$env:USERPROFILE/fitty-release.p12"
$buildTools = "$env:ANDROID_HOME/build-tools/36.0.0"
$versionCode = 3
$releaseDir = 'app/build/outputs/apk/release'
$apk = "$releaseDir/fitty-$versionCode.apk"
.\gradlew.bat :app:assembleRelease "-PfittyVersionCode=$versionCode"
if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
& "$buildTools/apksigner.bat" sign --ks "$keystore" --ks-key-alias fitty --out "$apk" "$releaseDir/app-release-unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$buildTools/apksigner.bat" verify --print-certs "$apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$buildTools/aapt.exe" dump badging "$apk" | Select-String '^package:'
```

For an update, compare the `Signer #1 certificate SHA-256 digest` with the previous signed APK and check that `aapt` reports `com.delaynore.fitty` and the new `versionCode`. [Android signing and verification](https://developer.android.com/tools/apksigner)

## Install or update Fitty

Copy the signed `fitty-<versionCode>.apk` to the phone and open it in the Files app. If Android asks, allow that app to install APKs. Tap Install or Update. Open Fitty from its launcher icon.

With USB debugging enabled, you can also install the APK from the computer. Use `adb devices` to find the device serial and pass it in place of `DEVICE_SERIAL`. The `-r` flag replaces the installed app and keeps its data. [Android Debug Bridge](https://developer.android.com/tools/adb)

```powershell
& "$env:ANDROID_HOME/platform-tools/adb.exe" -s DEVICE_SERIAL install -r "$apk"
```

To verify an update, keep the previous version installed and save at least two training programs. Select one for Home, log a set in a completed workout, and leave another workout in progress with a logged set. Install the new APK over the existing app. With airplane mode on and Wi-Fi off, open Fitty from its icon and check the program list, the selected program on Home, the in-progress workout and its logged set, and the completed workout in History. If the Room schema changed, create the records in the previous version so the update exercises the migration.
