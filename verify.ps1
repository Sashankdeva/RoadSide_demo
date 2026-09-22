<#
    RoadSide end-to-end verification:
    build -> install -> launch -> test -> collect logs -> report

    Usage:
      .\verify.ps1              # build, install, run the full instrumented suite
      .\verify.ps1 -Offline     # additionally run the full suite with every radio off
      .\verify.ps1 -SkipBuild   # reuse the existing APKs

    Push real recordings first (optional) with:
      adb push clip.wav /sdcard/Android/data/com.roadside/files/roadside_test/

    Exit code is 0 only if every test pass passed (and, with -Offline, the device was
    confirmed to have no network during the offline pass).
#>
param(
    [switch]$Offline,
    [switch]$SkipBuild
)

# Native tools (gradlew, adb) write progress and warnings to stderr. Under
# Windows PowerShell 5.1 an 'Stop' preference turns those into terminating
# NativeCommandError records even on exit code 0, so exit codes are checked
# explicitly instead.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$adb  = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$apk  = Join-Path $root 'app\build\outputs\apk'
$out  = Join-Path $root 'app\build\verify'
$runner = 'com.roadside.test/androidx.test.runner.AndroidJUnitRunner'
$logTags = @('RoadSideVerify:I', 'RoadSideEmbed:I', 'RoadSideVision:I', 'RoadSideFlow:I', 'RoadSideRecorder:I', 'RoadSideSpecialist:I',
             'YamNetEmbedding:I', 'ChainConditionHead:I', '*:S')
$failures = New-Object System.Collections.Generic.List[string]

function Section($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Adb-Get($cmd) { ((& $adb shell $cmd) | Out-String).Trim() }

if (-not (Test-Path $out)) { New-Item -ItemType Directory -Force $out | Out-Null }

# Runs the WHOLE instrumented suite (no class filter) and returns a summary.
# Logcat is streamed to a file while the tests run: the device log buffer is too small
# to hold the full report, and reading it afterwards with `logcat -d` silently loses the
# earliest tests' output.
function Invoke-Suite($label) {
    $instFile = Join-Path $out "instrument_$label.txt"
    $logFile  = Join-Path $out "logcat_$label.txt"
    & $adb logcat -c
    $lc = Start-Process -FilePath $adb -PassThru -NoNewWindow `
        -ArgumentList (@('logcat', '-v', 'tag', '-s') + $logTags) `
        -RedirectStandardOutput $logFile
    try {
        & $adb shell am instrument -w -r -e package com.roadside $runner 2>&1 |
            Out-File -Encoding utf8 $instFile
    } finally {
        Start-Sleep -Milliseconds 500
        Stop-Process -Id $lc.Id -Force -ErrorAction SilentlyContinue
    }
    $text = Get-Content $instFile -Raw
    $okMatch   = [regex]::Match($text, 'OK \((\d+) tests?\)')
    $failMatch = [regex]::Match($text, 'Tests run: (\d+),\s+Failures: (\d+)')
    # Each test reports a status block ending in INSTRUMENTATION_STATUS_CODE; -2 = failure.
    # Split into blocks so the failing test's own name is reported, not an earlier one.
    $blocks = $text -split '(?=INSTRUMENTATION_STATUS_CODE:)'
    $failed = @()
    for ($k = 1; $k -lt $blocks.Count; $k++) {
        if ($blocks[$k] -match '^INSTRUMENTATION_STATUS_CODE: -2') {
            $m = [regex]::Matches($blocks[$k - 1], 'INSTRUMENTATION_STATUS: test=(\w+)')
            if ($m.Count -gt 0) { $failed += $m[$m.Count - 1].Groups[1].Value }
        }
    }
    $r = [pscustomobject]@{
        Label  = $label
        Passed = $okMatch.Success
        Count  = if ($okMatch.Success) { [int]$okMatch.Groups[1].Value } elseif ($failMatch.Success) { [int]$failMatch.Groups[1].Value } else { 0 }
        Failed = $failed
        Time   = ([regex]::Match($text, 'Time: ([\d.,]+)')).Groups[1].Value
        Log    = $logFile
        Inst   = $instFile
    }
    if ($r.Passed) {
        Write-Host ("{0}: OK ({1} tests) in {2} s" -f $label, $r.Count, $r.Time) -ForegroundColor Green
    } else {
        Write-Host ("{0}: FAILED - {1} tests run, failed: {2}" -f $label, $r.Count, ($failed -join ', ')) -ForegroundColor Red
        Write-Host "  full output: $instFile"
        $failures.Add("$label test suite failed")
    }
    return $r
}

function Show-Crashes($label) {
    $crashes = & $adb logcat -d -b crash,main |
        Select-String -Pattern 'FATAL EXCEPTION|ANR in com\.roadside|AndroidRuntime.*com\.roadside'
    if ($crashes) { $crashes; $failures.Add("$label crash/ANR") }
    else { Write-Host "$label`: no crashes or ANRs." -ForegroundColor Green }
}

# True only when the device has no default network AND an IP-level probe fails.
function Test-DeviceOffline {
    $active = (& $adb shell dumpsys connectivity | Select-String 'Active default network:' |
        Select-Object -First 1).ToString().Trim()
    & $adb shell ping -c 1 -W 2 8.8.8.8 *> $null
    $pingOk = ($LASTEXITCODE -eq 0)
    Write-Host "  $active ; ping 8.8.8.8 = $(if ($pingOk) { 'REACHABLE' } else { 'unreachable' })"
    return ($active -match 'Active default network: none') -and (-not $pingOk)
}

# ── Gradle AF_UNIX workaround ────────────────────────────────────────────────
# Gradle's daemon needs Selector.open(), which on Windows creates an AF_UNIX
# socket under java.io.tmpdir. On this machine something (a security product
# filter) blocks AF_UNIX socket files anywhere under %LOCALAPPDATA%, so
# Selector.open() fails with "Unable to establish loopback connection" and no
# Gradle command can run. Pointing TEMP at a directory outside LOCALAPPDATA
# fixes it for the whole process tree (client + daemon). Diagnosis in BENCHMARK.md.
$jt = 'C:\Temp\jt'
if (-not (Test-Path $jt)) { New-Item -ItemType Directory -Force $jt | Out-Null }
$env:TEMP = $jt
$env:TMP  = $jt

if (-not $SkipBuild) {
    Section 'BUILD'
    & (Join-Path $root 'gradlew.bat') :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    Get-ChildItem -Recurse $apk -Filter *.apk |
        ForEach-Object { '{0,-40} {1,8:N2} MB' -f $_.Name, ($_.Length / 1MB) }
}

Section 'DEVICE'
& $adb devices -l
$model = Adb-Get 'getprop ro.product.model'
$abi   = Adb-Get 'getprop ro.product.cpu.abi'
$page  = Adb-Get 'getconf PAGESIZE'
Write-Host "model=$model abi=$abi pagesize=$page"

Section 'INSTALL'
# ColorOS gates adb installs behind an on-device confirmation. Disabling the
# verifier avoids that prompt; it is restored at the end of this block.
$verifier = Adb-Get 'settings get global verifier_verify_adb_installs'
& $adb shell settings put global verifier_verify_adb_installs 0 | Out-Null
try {
    & $adb install -r -t -d (Join-Path $apk 'debug\app-debug.apk')
    & $adb install -r -t -d (Join-Path $apk 'androidTest\debug\app-debug-androidTest.apk')
} finally {
    if ($verifier -and $verifier -ne 'null') {
        & $adb shell settings put global verifier_verify_adb_installs $verifier | Out-Null
    }
}
$perms = & $adb shell dumpsys package com.roadside | Select-String 'android.permission.INTERNET'
if ($perms) { Write-Host 'WARNING: app requests INTERNET' -ForegroundColor Red; $failures.Add('INTERNET permission present') }
else { Write-Host 'INTERNET permission: not requested' -ForegroundColor Green }

Section 'PUSH VALIDATION DATA'
# Offline demo data: audio clips and labelled chain images. Filenames carry the
# expected label so the instrumented tests can score themselves on device.
$visionSrc = 'C:\Codes\Datasets\roadside_vision\device_images'
$visionHeldOut = 'C:\Codes\Datasets\roadside_vision\device_images_heldout'
$audioSrc  = 'C:\Codes\Datasets\roadside_validation\device_subset'
# The directory is cleared first. It used to accumulate images across sessions — 145 files
# from three different pushes, including two images present twice under different names —
# so the per-image scores depended on the phone's history rather than on what is on disk.
if (Test-Path $visionSrc) {
    & $adb shell 'rm -rf /sdcard/Android/data/com.roadside/files/vision_test'
    & $adb shell 'mkdir -p /sdcard/Android/data/com.roadside/files/vision_test'
    & $adb push "$visionSrc/." '/sdcard/Android/data/com.roadside/files/vision_test/'
    # Images from the v2 test split that the head never trained on, deduplicated against
    # device_images by content hash.
    if (Test-Path $visionHeldOut) {
        & $adb push "$visionHeldOut/." '/sdcard/Android/data/com.roadside/files/vision_test/'
    }
} else { Write-Host 'no vision_test images to push' }
if (Test-Path $audioSrc) {
    & $adb shell 'mkdir -p /sdcard/Android/data/com.roadside/files/roadside_test'
    & $adb push "$audioSrc/." '/sdcard/Android/data/com.roadside/files/roadside_test/'
} else { Write-Host 'no audio clips to push' }
# Labelled clips for ChainAudioSpecialistTest (neg__ must never produce chain evidence).
$specSrc = 'C:\Codes\Datasets\roadside_chain_audio\device_specialist_test'
if (Test-Path $specSrc) {
    & $adb shell 'mkdir -p /sdcard/Android/data/com.roadside/files/specialist_test'
    & $adb push "$specSrc/." '/sdcard/Android/data/com.roadside/files/specialist_test/'
} else { Write-Host 'no specialist_test clips to push' }

Section 'LAUNCH'
& $adb logcat -c
& $adb shell am force-stop com.roadside
& $adb shell am start -W -n com.roadside/.MainActivity

Section 'TEST (full suite)'
$online = Invoke-Suite 'online'

Section 'REPORT'
Get-Content $online.Log | ForEach-Object { $_ -replace '^\w/[\w]+\s*:\s?', '' }
Write-Host "(log: $($online.Log))"

Section 'CRASH CHECK'
Show-Crashes 'online'

if ($Offline) {
    Section 'OFFLINE (airplane mode + Wi-Fi off + Bluetooth off)'
    # The app declares no INTERNET permission, so it cannot reach the network at all.
    # This pass confirms the whole suite also runs with no connectivity. Airplane mode
    # alone is NOT enough on ColorOS: the OnePlus 13R keeps Wi-Fi connected in airplane
    # mode (wifi_on=2), so Wi-Fi and Bluetooth are switched off explicitly and the
    # absence of a network is checked before the tests are trusted.
    $prevAirplane = Adb-Get 'settings get global airplane_mode_on'
    $prevWifi     = Adb-Get 'settings get global wifi_on'
    $prevBt       = Adb-Get 'settings get global bluetooth_on'
    Write-Host "before: airplane=$prevAirplane wifi=$prevWifi bluetooth=$prevBt"
    try {
        & $adb shell cmd connectivity airplane-mode enable | Out-Null
        & $adb shell svc wifi disable | Out-Null
        & $adb shell svc bluetooth disable | Out-Null

        $isOffline = $false
        for ($i = 0; $i -lt 10 -and -not $isOffline; $i++) {
            Start-Sleep -Seconds 2
            $isOffline = Test-DeviceOffline
        }
        $a = Adb-Get 'settings get global airplane_mode_on'
        $w = Adb-Get 'settings get global wifi_on'
        $b = Adb-Get 'settings get global bluetooth_on'
        Write-Host "during: airplane=$a wifi=$w bluetooth=$b"

        if (-not $isOffline) {
            Write-Host 'OFFLINE NOT ACHIEVED - device still has network; offline pass not run.' -ForegroundColor Red
            $failures.Add('offline state not achieved')
        } else {
            Write-Host 'Device confirmed offline.' -ForegroundColor Green
            & $adb shell am force-stop com.roadside
            & $adb shell am start -W -n com.roadside/.MainActivity | Select-String 'Status|TotalTime'
            $offlineRun = Invoke-Suite 'offline'
            Get-Content $offlineRun.Log |
                Select-String 'INIT:|FIRST|WARM|WINDOWS:|INFERENCE RAN|RESULT:|embedding dimension|NON-ZERO|images=|full clip|audio ran on|vision ran on' |
                ForEach-Object { $_ -replace '^\w/[\w]+\s*:\s?', '' }
            Write-Host 're-check after tests:'
            if (-not (Test-DeviceOffline)) {
                Write-Host 'Network came back during the offline pass.' -ForegroundColor Red
                $failures.Add('network returned during offline pass')
            }
            Show-Crashes 'offline'
        }
    } finally {
        if ($prevAirplane -ne '1') { & $adb shell cmd connectivity airplane-mode disable | Out-Null }
        if ($prevWifi -ne '0')     { & $adb shell svc wifi enable | Out-Null }
        if ($prevBt -ne '0')       { & $adb shell svc bluetooth enable | Out-Null }
        Start-Sleep -Seconds 3
        Write-Host ("restored: airplane={0} wifi={1} bluetooth={2}" -f `
            (Adb-Get 'settings get global airplane_mode_on'),
            (Adb-Get 'settings get global wifi_on'),
            (Adb-Get 'settings get global bluetooth_on'))
    }
}

Section 'DONE'
if ($failures.Count -eq 0) {
    Write-Host 'ALL CHECKS PASSED' -ForegroundColor Green
    exit 0
} else {
    Write-Host ("FAILED: " + ($failures -join '; ')) -ForegroundColor Red
    exit 1
}
