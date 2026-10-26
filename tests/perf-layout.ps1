# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Legacy entry point; isolated Gradle fixtures preserve all sizing and integration assertions.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'host-tests' -TestTask 'readoutTest' -TestClass @('io.github.fengwenhua.colorduo.PerfLayoutTest','io.github.fengwenhua.colorduo.ReadoutLayoutSourceContractTest')
