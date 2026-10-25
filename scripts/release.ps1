# Thin Windows entry point for the protected GitHub Actions release workflow.
# Create/push a clean source tag first. No local signing or default pruning.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [switch]$DryRun,
    [switch]$PruneOldReleases
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Stamp = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $Root 'version.properties')))
if ($Version -ne $Stamp.versionName -or $Version -notmatch '^\d+\.\d+\.\d+$') {
    throw 'Version must match the checked-in formal version.properties stamp.'
}
Push-Location $Root
try {
    $Tag = "v$Version"
    python3 scripts/github-release.py preflight --tag $Tag
    if ($LASTEXITCODE -ne 0) { throw 'Release preflight failed.' }
    if ($DryRun) { Write-Host 'Preflight passed; no workflow was dispatched.'; return }
    $Prune = if ($PruneOldReleases) { 'true' } else { 'false' }
    gh workflow run release.yml --repo fengwenhua/ColorDuo --ref main -f "tag=$Tag" -f "prune_old_releases=$Prune"
    if ($LASTEXITCODE -ne 0) { throw 'Workflow dispatch failed.' }
} finally { Pop-Location }
