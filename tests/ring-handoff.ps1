# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Compatibility entry for current Gradle regression coverage.
param([string]$SourceDirectory)
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'render-tests' -RenderSources 'lease' -TestClass @('io.github.fengwenhua.colorduo.RingHandoffTest', 'io.github.fengwenhua.colorduo.HyperOsIconLeaseTest') -SourceDirectory $SourceDirectory
