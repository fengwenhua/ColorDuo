# ColorDuo offline build pipeline.
#
# No Gradle: the module needs nothing beyond a manifest and its own classes, so
# javac -> d8 -> aapt2 -> zipalign -> apksigner is deterministic and avoids
# pulling hundreds of MB through a slow proxy.
#
# Usage:  pwsh -File build.ps1

$ErrorActionPreference = 'Continue'

$Root      = 'C:\code\ColorDuo'
$AppDir    = Join-Path $Root 'app\src\main'
$BuildDir  = Join-Path $Root 'build'
# 21 rather than 17 so the offline pipeline and the Gradle build share one JDK.
# This path only compiles -source 8 Java, but a second JDK in the repo is one
# more thing to keep in sync for no benefit.
$JavaHome  = Join-Path $Root '.tools\jdk\jdk-21.0.12.1+1'
$Sdk       = Join-Path $Root '.tools\sdk'
$BuildTools= Join-Path $Sdk 'build-tools\37.0.0'
$AndroidJar= Join-Path $Sdk 'platforms\android-37.0\android.jar'
$Libs      = Join-Path $Root '.tools\libs'

$Javac     = Join-Path $JavaHome 'bin\javac.exe'
$Jar       = Join-Path $JavaHome 'bin\jar.exe'
$KeyTool   = Join-Path $JavaHome 'bin\keytool.exe'
$D8        = Join-Path $BuildTools 'd8.bat'
$Aapt2     = Join-Path $BuildTools 'aapt2.exe'
$ZipAlign  = Join-Path $BuildTools 'zipalign.exe'
$ApkSigner = Join-Path $BuildTools 'apksigner.bat'

$MinSdk    = 29
$TargetSdk = 37
$ApkName   = 'ColorDuo.apk'
$OutApk    = Join-Path $Root "dist\$ApkName"

function Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# d8.bat / apksigner.bat resolve java through JAVA_HOME; the SDK tools also want
# it on PATH.
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"

foreach ($tool in @($Javac, $D8, $Aapt2, $ZipAlign, $ApkSigner)) {
    if (-not (Test-Path $tool)) { throw "missing build tool: $tool" }
}
if (-not (Test-Path $AndroidJar)) { throw "missing android.jar: $AndroidJar" }

if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
New-Item -ItemType Directory -Path $BuildDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Root 'dist') -Force | Out-Null

# ---------------------------------------------------------------- 1. javac
Step 'compiling java sources'
$ClassesDir = Join-Path $BuildDir 'classes'
New-Item -ItemType Directory -Path $ClassesDir | Out-Null

$Sources = Get-ChildItem -Path (Join-Path $AppDir 'java') -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if ($Sources.Count -eq 0) { throw 'no java sources found' }
Write-Host "    $($Sources.Count) source file(s)"

$ClassPath = @(
    (Join-Path $Libs 'classes\api-102.0.0\classes.jar'),
    (Join-Path $Libs 'annotation-1.0.0.jar')
) -join ';'

$JavacArgs = @(
    '-nowarn',
    '-source', '8',
    '-target', '8',
    '-bootclasspath', $AndroidJar,
    '-classpath', $ClassPath,
    '-encoding', 'UTF-8',
    '-d', $ClassesDir
) + $Sources

& $Javac @JavacArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

# ------------------------------------------------------------------- 2. d8
Step 'dexing'
$ClassesJar = Join-Path $BuildDir 'classes.jar'
& $Jar --create --file $ClassesJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed ($LASTEXITCODE)" }

$DexDir = Join-Path $BuildDir 'dex'
New-Item -ItemType Directory -Path $DexDir | Out-Null
& $D8 --lib $AndroidJar --min-api $MinSdk --output $DexDir $ClassesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed ($LASTEXITCODE)" }
if (-not (Test-Path (Join-Path $DexDir 'classes.dex'))) { throw 'd8 produced no classes.dex' }

# --------------------------------------------------------------- 3. aapt2
Step 'linking resources'
$BaseApk = Join-Path $BuildDir 'base.apk'
& $Aapt2 link `
    -o $BaseApk `
    --manifest (Join-Path $AppDir 'AndroidManifest.xml') `
    -I $AndroidJar `
    --min-sdk-version $MinSdk `
    --target-sdk-version $TargetSdk `
    --version-code 1 `
    --version-name '1.0'
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed ($LASTEXITCODE)" }

# ------------------------------------------------- 4. payload + xposed meta
Step 'packaging classes.dex and META-INF/xposed'
& $Jar --update --file $BaseApk -C $DexDir classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar update (dex) failed ($LASTEXITCODE)" }

# -C must point at the directory *containing* META-INF so the stored entry name
# is META-INF/xposed/<file>.
$ResDir = Join-Path $AppDir 'resources'
foreach ($f in @('module.prop', 'java_init.list', 'scope.list')) {
    $src = Join-Path $ResDir "META-INF\xposed\$f"
    if (-not (Test-Path $src)) { throw "missing xposed metadata: $src" }
    & $Jar --update --file $BaseApk -C $ResDir "META-INF/xposed/$f"
    if ($LASTEXITCODE -ne 0) { throw "jar update ($f) failed ($LASTEXITCODE)" }
}
# jar --update writes entries into META-INF/xposed/ only if the source path maps
# there; verify rather than assume.
$entries = & $Jar --list --file $BaseApk
foreach ($need in @('classes.dex', 'META-INF/xposed/module.prop',
                    'META-INF/xposed/java_init.list', 'META-INF/xposed/scope.list')) {
    if (-not ($entries -contains $need)) { throw "APK is missing $need" }
}

# ------------------------------------------------------ 5. align and sign
Step 'aligning and signing'
$AlignedApk = Join-Path $BuildDir 'aligned.apk'
& $ZipAlign -f 4 $BaseApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

$Keystore = Join-Path $Root '.tools\debug.keystore'
if (-not (Test-Path $Keystore)) {
    Step 'generating debug keystore'
    & $KeyTool -genkeypair -v `
        -keystore $Keystore `
        -alias hyperduo `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass android -keypass android `
        -dname 'CN=ColorDuo, O=ColorDuo, C=CN'
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
}

& $ApkSigner sign `
    --ks $Keystore `
    --ks-key-alias hyperduo `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $OutApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed ($LASTEXITCODE)" }

Step 'verifying signature'
& $ApkSigner verify --print-certs $OutApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed ($LASTEXITCODE)" }

$size = (Get-Item $OutApk).Length
Write-Host ''
Write-Host "built $OutApk ($size bytes)" -ForegroundColor Green
