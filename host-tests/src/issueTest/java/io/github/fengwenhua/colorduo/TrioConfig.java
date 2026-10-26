/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;
final class TrioConfig {
 static TrioSettings settings=TrioSettings.defaults();
 static TrioSettings get(){return settings;} static TrioAppearance appearance(){return TrioAppearance.of(settings);}
}
