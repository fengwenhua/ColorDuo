# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Drawing-region pixels, transforms and editor contracts.
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'render-tests' -TestClass @(
    'io.github.fengwenhua.colorduo.DrawingZonesTest',
    'io.github.fengwenhua.colorduo.DrawingSourceContractTest'
)
