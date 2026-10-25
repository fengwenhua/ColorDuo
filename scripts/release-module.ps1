# Authorized fallback: publish the same four assets from an Actions signed-release artifact.
# GITHUB_TOKEN must be scoped to this module repository. No rebuild or asset replacement.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$ModuleRepo,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($ModuleRepo -ne 'Xposed-Modules-Repo/io.github.fengwenhua.colorduo') { throw 'Unexpected module repository.' }
$Stamp = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $Root 'version.properties')))
if ($Version -ne $Stamp.versionName -or $VersionCode -ne [int]$Stamp.versionCode) { throw 'Version mismatch.' }
$Apk = (Resolve-Path $Apk).Path
$Dist = Split-Path $Apk -Parent
foreach ($Name in @("ColorDuo-$Version.apk", "ColorDuo-$Version.apk.sha256", 'release-manifest.json', 'release-manifest.sig')) {
    if (-not (Test-Path (Join-Path $Dist $Name))) { throw "Missing signed artifact: $Name" }
}
if ($DryRun) { Write-Host 'Asset paths checked; publishing skipped.'; return }
Push-Location $Root
try {
    python3 scripts/github-release.py publish --repo $ModuleRepo --tag "$VersionCode-$Version" --dist $Dist
    if ($LASTEXITCODE -ne 0) { throw 'Module publishing failed. Check organization authorization.' }
} finally { Pop-Location }
