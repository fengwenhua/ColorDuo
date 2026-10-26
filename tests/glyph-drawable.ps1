# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Legacy entry point; executable assertions are maintained in Gradle/JUnit.
param([string]$SourceDirectory)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'host-tests' -TestClass @('io.github.fengwenhua.colorduo.GlyphHostTest','io.github.fengwenhua.colorduo.GlyphFallbackTest','io.github.fengwenhua.colorduo.GlyphSourceContractTest') -SourceDirectory $SourceDirectory
