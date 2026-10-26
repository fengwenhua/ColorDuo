/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package android.view;
public class WindowInsets {
 public boolean visible=true; public int top=121;
 public boolean isVisible(int type){return visible;}
 public Insets getInsets(int type){return new Insets(top);}
 public static class Type { public static int statusBars(){return 1;} }
 public static class Insets { public int top; Insets(int t){top=t;} }
}
