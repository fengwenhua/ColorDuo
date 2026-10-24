# ColorDuo local toolchain environment
# Dot-source this at the start of any build/decompile shell:
#   . .\scripts\env.ps1

$env:COLORDUO_ROOT = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# JDK 21, not 17: Miuix ships Java 21 (class file major 65) bytecode, and the Kotlin
# compiler refuses to inline a 21-targeted function into 17-targeted output. The
# nav entry points (rememberNavController, entry) are inline composables, so the
# build cannot go below 21 while miuix-nav is in the dependency list. javac has to
# be 21 as well: AGP rejects a Java/Kotlin target mismatch outright.
$env:JAVA_HOME    = "$env:COLORDUO_ROOT\.tools\jdk\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "$env:COLORDUO_ROOT\.tools\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "$env:COLORDUO_ROOT\.tools\gradle-home"
$env:PATH = "$env:JAVA_HOME\bin;$env:COLORDUO_ROOT\.tools\bin;$env:COLORDUO_ROOT\.tools\bin\jadx\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

# Gradle (no system install; local copy under .tools)
$gradleHome = Get-ChildItem "$env:COLORDUO_ROOT\.tools\gradle" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($gradleHome) {
    $env:GRADLE_HOME = $gradleHome.FullName
    $env:PATH = "$env:GRADLE_HOME\bin;$env:PATH"
}

$env:ADB = 'C:\Program Files\UotanToolbox\Bin\platform-tools\adb.exe'

function apktool { & "$env:JAVA_HOME\bin\java.exe" -jar "$env:COLORDUO_ROOT\.tools\bin\apktool.jar" @args }
function jadx    { & "$env:COLORDUO_ROOT\.tools\bin\jadx\bin\jadx.bat" @args }
function java21  { & "$env:JAVA_HOME\bin\java.exe" @args }
function gradle  { & "$env:GRADLE_HOME\bin\gradle.bat" @args }
function adb     { & $env:ADB @args }

Write-Host "[ColorDuo] JAVA_HOME=$env:JAVA_HOME" -ForegroundColor DarkCyan
Write-Host "[ColorDuo] ANDROID_HOME=$env:ANDROID_HOME" -ForegroundColor DarkCyan
if ($env:GRADLE_HOME) { Write-Host "[ColorDuo] GRADLE_HOME=$env:GRADLE_HOME" -ForegroundColor DarkCyan }
