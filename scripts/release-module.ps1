# ColorDuo - publish one release into the LSPosed module repository.
#
# release.ps1 calls this only when -ModuleRepo is explicitly supplied, keeping
# both repos on the same version. It also stays runnable standalone, to
# backfill a module-repo step that failed after the main repo already shipped.
# The main repo is the website / download home, while
# this puts the same APK where the LSPosed app looks for it. It is a separate
# script because the module repo is a different repository with its own spec.
#
# Apply for a separate ColorDuo module repository before using this script.
# Its owner/name must be supplied explicitly; there is no upstream default.
#
# Spec (Xposed-Modules-Repo/submission README):
#   Release Tag   = [version code]-[version name]   e.g. 10400-1.4
#   Release Title = the version name                e.g. 1.4
#   Release body  = the changelog
#   asset         = the apk
#
# Usage (from the repository root; this script lives in scripts\):
#   .\scripts\release-module.ps1 -ModuleRepo owner/repo -Version 1.0.0 -VersionCode 10000 `
#       -Apk .\dist\ColorDuo-1.4.apk -NotesFile .\docs\release-notes\release-notes-1.4.md
#   .\scripts\release-module.ps1 ... -DryRun     # validate badging only, create nothing
#   .\scripts\release-module.ps1 ... -SkipReadme # do not touch the module repo README
#
# After a successful publish this deletes every other release in the module repo,
# so the repo always shows exactly one version -- the same "only the newest
# release" policy release.ps1 applies to the main repo. Tags are left alone.
#
# The module repo's README.md IS published from .\module-README.md (tracked in
# this repo) so the published copy cannot drift away from the source. Pass
# -SkipReadme to leave it alone. SUMMARY is NOT touched here: it has no tracked
# source and goes through PUT /contents by hand (see docs/DEVELOPMENT.md).
#
# ASCII only, and NO BOM: this file has no Chinese, and PowerShell 5.1 reads a
# BOM-less .ps1 with the ANSI code page. Keep every byte < 0x80 so that is safe.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$ModuleRepo,
    [string]$Notes,
    [string]$NotesFile,
    [switch]$DryRun,
    [switch]$Force,
    [switch]$KeepOldReleases,
    [switch]$SkipReadme
)

$ErrorActionPreference = 'Stop'

$Root    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tools   = Join-Path $Root '.tools'
$Module  = $ModuleRepo
$Package = 'io.github.fengwenhua.colorduo'
$Tag     = "$VersionCode-$Version"
$Asset   = "ColorDuo-$Version.apk"
$Readme  = Join-Path $Root 'module-README.md'

$Aapt = Join-Path $Tools 'sdk\build-tools\37.0.0\aapt2.exe'

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Exe @Arguments 2>&1
        return [pscustomobject]@{
            Output   = (@($output | ForEach-Object { "$_" }) -join "`n").Trim()
            ExitCode = $LASTEXITCODE
        }
    } finally { $ErrorActionPreference = $prev }
}

function Get-GitHubToken {
    $tmp = Join-Path $env:TEMP ("hd_cred_" + [guid]::NewGuid().ToString('N') + '.txt')
    try {
        [System.IO.File]::WriteAllText($tmp, "protocol=https`nhost=github.com`n`n")
        $raw = (Invoke-Native 'cmd' @('/c', "git credential fill < `"$tmp`"")).Output
        $line = $raw -split "`n" | Where-Object { $_ -match '^password=' } | Select-Object -First 1
        if (-not $line) { throw 'GitHub credential unavailable: git credential fill returned no password.' }
        return ($line -replace '^password=', '').Trim()
    } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
}

# The module repo must show exactly one version: delete every release except the
# one just published. Only the release records go away; the tags stay.
function Remove-StaleReleases {
    param(
        [string]$ApiBase,
        [hashtable]$Headers,
        [int]$KeepId,
        [string]$KeepTag
    )
    $all = Invoke-RestMethod -Uri "$ApiBase/releases?per_page=100" -Headers $Headers
    $stale = @($all | Where-Object { $_.id -ne $KeepId -and -not $_.draft })
    if ($stale.Count -eq 0) {
        Write-Host '==> no stale releases to remove' -ForegroundColor DarkGray
        return
    }
    foreach ($r in $stale) {
        Write-Host "==> deleting stale release $($r.tag_name) (tag kept)" -ForegroundColor Yellow
        Invoke-RestMethod -Method Delete -Uri "$ApiBase/releases/$($r.id)" -Headers $Headers | Out-Null
    }
    Write-Host "==> removed $($stale.Count) stale release(s); only $KeepTag remains" -ForegroundColor Green
}

# ---- validate the apk --------------------------------------------------------

if (-not (Test-Path $Apk)) { throw "apk not found: $Apk" }
$Apk = (Resolve-Path $Apk).Path

$badgingRun = Invoke-Native $Aapt @('dump', 'badging', $Apk)
$badging = [string]($badgingRun.Output -split "`n" | Where-Object { $_ -match '^package:' } | Select-Object -First 1)
if (-not $badging) { throw "cannot read badging from $Apk" }

Write-Host "==> $Apk" -ForegroundColor Cyan
Write-Host "    $badging"

$errors = @()
if ($badging -notmatch "name='$([regex]::Escape($Package))'") { $errors += "package is not $Package" }
if ($badging -notmatch "versionCode='$VersionCode'")        { $errors += "versionCode is not $VersionCode" }
if ($badging -notmatch "versionName='$([regex]::Escape($Version))'") { $errors += "versionName is not $Version" }
if ($errors.Count) { throw ("apk does not match the intended release: " + ($errors -join '; ')) }
Write-Host "    [ok] package / versionCode / versionName all match" -ForegroundColor Green
Write-Host "==> target: $Module  tag=$Tag  asset=$Asset"

if ($DryRun) { Write-Host '==> -DryRun: nothing was created' -ForegroundColor Yellow; return }

# ---- changelog ---------------------------------------------------------------

if (-not $Notes -and $NotesFile) {
    if (-not (Test-Path $NotesFile)) { throw "notes file not found: $NotesFile" }
    $Notes = [System.IO.File]::ReadAllText((Resolve-Path $NotesFile), [Text.Encoding]::UTF8)
}
if (-not $Notes) { throw 'a changelog is required: pass -Notes or -NotesFile (the release body is the changelog).' }

$token = Get-GitHubToken
$headers = @{
    Authorization = "token $token"
    'User-Agent'  = 'ColorDuo-Release'
    Accept        = 'application/vnd.github+json'
}
$api = "https://api.github.com/repos/$Module"

# ---- existing release with this tag? ----------------------------------------

$existing = $null
try {
    $existing = Invoke-RestMethod -Uri "$api/releases/tags/$Tag" -Headers $headers
} catch {
    $status = $null
    try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = $null }
    if ($status -ne 404) { throw }
}

$payload = @{
    tag_name   = $Tag
    name       = $Version
    body       = [string]$Notes
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

if ($existing) {
    if (-not $Force) { throw "release $Tag already exists in the module repo. Re-run with -Force to overwrite." }
    Write-Host "==> overwriting existing release $Tag" -ForegroundColor Yellow
    $release = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($existing.id)" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
} else {
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
}

# ---- asset -------------------------------------------------------------------

foreach ($a in $release.assets) {
    if ($a.name -eq $Asset) {
        Write-Host "==> deleting stale asset $Asset" -ForegroundColor Yellow
        Invoke-RestMethod -Method Delete -Uri "$api/releases/assets/$($a.id)" -Headers $headers | Out-Null
    }
}

$uploaded = Invoke-RestMethod -Method Post `
    -Uri "https://uploads.github.com/repos/$Module/releases/$($release.id)/assets?name=$Asset" `
    -Headers $headers -ContentType 'application/vnd.android.package-archive' -InFile $Apk

Write-Host "==> uploaded $($uploaded.name)  $($uploaded.size) B" -ForegroundColor Green
Write-Host "==> $($uploaded.browser_download_url)" -ForegroundColor Green

# ---- readme ------------------------------------------------------------------

# module-README.md is the tracked source of the module repo's front page. If the
# published copy already matches, say so and do not create an empty commit.
if ($SkipReadme) {
    Write-Host '==> -SkipReadme: leaving the module repo README.md alone' -ForegroundColor Yellow
} elseif (-not (Test-Path $Readme)) {
    Write-Host "==> $Readme not found: skipping README.md" -ForegroundColor Yellow
} else {
    $want = [System.IO.File]::ReadAllText($Readme, [Text.Encoding]::UTF8)
    $cur = $null
    try { $cur = Invoke-RestMethod -Uri "$api/contents/README.md" -Headers $headers } catch {
        $status = $null
        try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = $null }
        if ($status -ne 404) { throw }
    }
    $have = ''
    if ($cur) { $have = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($cur.content -replace '\s', ''))) }

    if ($cur -and $have -ceq $want) {
        Write-Host '==> README.md already up to date' -ForegroundColor DarkGray
    } else {
        $body = @{
            message = "docs: sync README for $Version"
            content = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($want))
        }
        if ($cur) { $body.sha = $cur.sha }
        $put = Invoke-RestMethod -Method Put -Uri "$api/contents/README.md" -Headers $headers `
            -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress)
        Write-Host "==> README.md published  $($put.content.size) B  sha=$($put.content.sha.Substring(0,10))" -ForegroundColor Green
    }
}

# Run last, so a cleanup failure can never undo a successful publish.
if ($KeepOldReleases) {
    Write-Host '==> -KeepOldReleases: keeping the previous releases' -ForegroundColor Yellow
} else {
    Remove-StaleReleases -ApiBase $api -Headers $headers -KeepId $release.id -KeepTag $Tag
}

Write-Host '==> still required for listing: the repo DESCRIPTION must be set by hand in the web UI.'
