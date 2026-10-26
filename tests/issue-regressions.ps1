# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Historical issue suites and integration contracts now run in isolated Gradle fixtures.
param(
    [string]$SourceDirectory,
    [ValidateSet('signal','tint','assignment','wifi','clear','clock','centre','dots','outorder','capsule','isolation','nudges')]
    [string[]]$Suite = @('signal','tint','assignment','wifi','clear','clock','centre','dots','outorder','capsule','isolation','nudges')
)
$IssueTests = @()
foreach ($IssueSuite in $Suite) {
    $IssueTests += "io.github.fengwenhua.colorduo.IssueRegressionTest.${IssueSuite}Regression"
}
$IssueTests += 'io.github.fengwenhua.colorduo.IssueSourceContractTest'
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'host-tests' -TestTask 'issueTest' -TestClass $IssueTests -SourceDirectory $SourceDirectory
