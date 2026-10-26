# Copyright (C) 2026 yixing233 and HyperDuo contributors
# SPDX-License-Identifier: GPL-3.0-only
# Compatibility launcher for the real capsule pixels and source integration pins.
& (Join-Path $PSScriptRoot 'gradle-test.ps1') -TestProject 'render-tests' -TestClass @(
    'io.github.fengwenhua.colorduo.CapsuleTest',
    'io.github.fengwenhua.colorduo.CapsuleSourceContractTest'
)
