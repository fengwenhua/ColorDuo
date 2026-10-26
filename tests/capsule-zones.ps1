# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Current four-cell capsule editor, pixel ownership and signal regression checks.
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'render-tests' -TestClass 'io.github.fengwenhua.colorduo.CapsuleZonesTest'
