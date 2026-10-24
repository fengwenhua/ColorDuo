# ColorDuo - install the signed APK on the connected device and watch the log.
#
# Usage:
#   & C:\code\ColorDuo\scripts\install.ps1              # install + enable in LSPosed + tail log
#   & C:\code\ColorDuo\scripts\install.ps1 -InstallOnly
#   & C:\code\ColorDuo\scripts\install.ps1 -LogOnly
#
# Notes:
#   * The device must be connected with USB debugging authorised.
#   * LSPosed requires a scope change to take effect: after the first install the
#     module must be enabled and com.android.systemui ticked in the manager, then
#     SystemUI restarted (this script does that).

param(
    [switch]$InstallOnly,
    [switch]$LogOnly
)

$ErrorActionPreference = 'Continue'

$Root     = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Adb      = 'C:\Program Files\UotanToolbox\Bin\platform-tools\adb.exe'
$Apk      = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
$Package  = 'io.github.fengwenhua.colorduo'

function Step($t) { Write-Host "==> $t" -ForegroundColor Cyan }

# --------------------------------------------------------------------- preflight
if (-not (Test-Path $Adb)) { Write-Error "adb not found: $Adb"; exit 1 }

$devices = & $Adb devices 2>&1 | Select-String -Pattern '\tdevice$'
if (-not $devices) {
    Write-Host "no device attached - connect the phone and authorise USB debugging" -ForegroundColor Yellow
    & $Adb devices -l 2>&1
    exit 1
}
Write-Host "device: $($devices -join ', ')" -ForegroundColor Green

if ($LogOnly) {
    Step 'clearing logcat'
    & $Adb logcat -c 2>&1 | Out-Null
    Step 'tailing LSPosedFramework / AndroidRuntime (Ctrl+C to stop)'
    & $Adb logcat -v time -s LSPosedFramework:* AndroidRuntime:E *:S
    exit 0
}

# ----------------------------------------------------------------------- install
Step "installing $Apk"
& $Adb install -r -d $Apk 2>&1
if ($LASTEXITCODE -ne 0) { Write-Error 'install failed'; exit 1 }

Step 'verifying the package is present'
& $Adb shell pm list packages 2>&1 | Select-String -Pattern $Package

Step 'confirming the modern-API entry point survived packaging'
& $Adb shell "unzip -l /data/app/*/io.github.fengwenhua.colorduo*/base.apk 2>/dev/null | grep -E 'xposed|classes.dex'" 2>&1

Step 'opening the settings screen'
& $Adb shell am start -n io.github.fengwenhua.colorduo/.ui.MainActivity 2>&1 | Out-Null

if ($InstallOnly) { exit 0 }

# ------------------------------------------------------- restart SystemUI + watch
Write-Host ''
Write-Host 'Next, in the LSPosed manager:' -ForegroundColor Yellow
Write-Host '  1. enable "Duo 三合一状态栏"'
Write-Host '  2. tick the scope "System UI" (com.android.systemui)'
Write-Host '  3. reboot, or let this script restart SystemUI below'
Write-Host ''
$answer = Read-Host 'Restart SystemUI now so the module loads? [y/N]'

if ($answer -eq 'y') {
    Step 'clearing logcat'
    & $Adb logcat -c 2>&1 | Out-Null

    Step 'restarting SystemUI'
    & $Adb shell "su -c 'killall com.android.systemui'" 2>&1

    Start-Sleep -Seconds 4
    Step 'tailing LSPosedFramework / AndroidRuntime (Ctrl+C to stop)'
    & $Adb logcat -v time -s LSPosedFramework:* AndroidRuntime:E *:S
} else {
    Write-Host 'skipped - run this script with -LogOnly when ready' -ForegroundColor Yellow
}
