# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Legacy entry point; executable assertions are maintained in Gradle/JUnit.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'host-tests' -TestClass @('io.github.fengwenhua.colorduo.BatteryPresenceTest','io.github.fengwenhua.colorduo.PresenceSourceContractTest')
