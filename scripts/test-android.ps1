[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^emulator-[0-9]{4,5}$')]
    [string]$Serial,

    [switch]$Smoke
)

$ErrorActionPreference = 'Stop'

if (-not $env:ANDROID_HOME) {
    throw 'Set ANDROID_HOME to the Android SDK directory.'
}
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
    throw "Android Debug Bridge not found at $adb."
}

$state = & $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') {
    throw "Emulator $Serial is not connected."
}
$emulator = & $adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or $emulator -ne '1') {
    throw "Device $Serial did not identify itself as an emulator."
}
$bootCompleted = & $adb -s $Serial shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or $bootCompleted -ne '1') {
    throw "Emulator $Serial has not finished booting."
}

Push-Location (Join-Path $PSScriptRoot '..')
try {
    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not build the debug and test APKs.'
    }

    foreach ($apk in @(
        'app/build/outputs/apk/debug/app-debug.apk',
        'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    )) {
        & $adb -s $Serial install -r $apk
        if ($LASTEXITCODE -ne 0) {
            throw "Could not install $apk on $Serial."
        }
    }

    $reportDirectory = New-Item -ItemType Directory -Path 'build/reports/android-device' -Force
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $log = Join-Path $reportDirectory.FullName "$Serial-$stamp.log"
    Write-Host "Instrumentation log: $log"

    $testFilter = @()
    if ($Smoke) {
        $testFilter = @('-e', 'annotation', 'com.delaynore.fitty.SmokeTest')
    }

    & $adb -s $Serial shell am instrument -w -r @testFilter com.delaynore.fitty.test/com.delaynore.fitty.FittyTestRunner |
        Tee-Object -FilePath $log

    if ($LASTEXITCODE -ne 0 -or -not (Select-String -Path $log -Pattern '^OK \([1-9][0-9]* tests?\)$' -Quiet)) {
        throw "Instrumented tests did not pass. See $log."
    }
} finally {
    Pop-Location
}
