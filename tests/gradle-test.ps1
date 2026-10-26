# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Compatibility launcher; test implementations and production fixtures live in Gradle.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('render-tests', 'host-tests')][string]$TestProject,
    [Parameter(Mandatory = $true)][string[]]$TestClass,
    [ValidateSet('test', 'readoutTest', 'stripTest', 'issueTest')][string]$TestTask = 'test',
    [ValidateSet('ring', 'lease')][string]$RenderSources = 'ring',
    [string]$SourceDirectory
)
$ErrorActionPreference = 'Stop'
$GradleTestRoot = Split-Path $PSScriptRoot -Parent
$GradleTestLauncher = Join-Path $GradleTestRoot 'gradlew'
if ($env:OS -eq 'Windows_NT') { $GradleTestLauncher += '.bat' }
$GradleTestArgs = @('--no-daemon', ":${TestProject}:${TestTask}")
foreach ($GradleTestClass in $TestClass) { $GradleTestArgs += @('--tests', $GradleTestClass) }
if ($SourceDirectory) {
    if ($TestProject -eq 'render-tests' -and $TestTask -eq 'test') {
        $GradleTestSourceProperty = 'ringTestProductionSources'
        if ($RenderSources -eq 'lease') { $GradleTestSourceProperty = 'leaseTestProductionSources' }
    } elseif ($TestProject -eq 'host-tests' -and $TestTask -ne 'readoutTest') {
        $GradleTestSourceProperty = 'hostTestProductionSources'
        if ($TestTask -eq 'stripTest') { $GradleTestSourceProperty = 'stripTestProductionSources' }
        if ($TestTask -eq 'issueTest') { $GradleTestSourceProperty = 'issueTestProductionSources' }
    } else { throw 'Alternate sources do not apply to this test task.' }
    $GradleTestArgs += "-P${GradleTestSourceProperty}=$((Resolve-Path $SourceDirectory).Path)"
}
Push-Location $GradleTestRoot
try {
    & $GradleTestLauncher @GradleTestArgs
    if ($LASTEXITCODE -ne 0) { throw 'Gradle regression verification failed.' }
    Write-Output "PASS: $TestProject / $($TestClass -join ', ')"
} finally { Pop-Location }
