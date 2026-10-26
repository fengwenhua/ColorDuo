/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.graphics;

/**
 * Marker stand-in for {@code android.graphics.Canvas}.
 *
 * <p>Deliberately empty: on the path under test the canvas is only handed on to
 * the renderer, which is stubbed, so no method of it is ever reached.
 */
public class Canvas { }
