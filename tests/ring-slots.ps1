param([string]$SourceDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$SourceDirectory) { $SourceDirectory = Join-Path $root 'app/src/main/java/io/github/fengwenhua/colorduo' }
$jdk = Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac = Join-Path $jdk 'javac.exe'; $java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }
$classes = Join-Path $root 'work/ring-slots/classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem -Recurse -Filter *.java (Join-Path $PSScriptRoot 'shims') | ForEach-Object FullName)
$sources += @('Prefs.java','ChargeLayout.java','SettingsSchema.java','TrioSettings.java','TrioAppearance.java','TrioGeometry.java','TrioZones.java','TrioRenderer.java') | ForEach-Object { Join-Path $SourceDirectory $_ }
$sources += Join-Path $PSScriptRoot 'RingSlotsTest.java'
& $javac -nowarn -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE) { throw 'Free-ring test compilation failed' }
& $java -Xmx512m -cp $classes io.github.fengwenhua.colorduo.RingSlotsTest
if ($LASTEXITCODE) { throw 'Free-ring tests failed' }
