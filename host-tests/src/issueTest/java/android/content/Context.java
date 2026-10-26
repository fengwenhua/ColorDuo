/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package android.content;
public class Context {
 public static final String WINDOW_SERVICE="window";
 public final android.view.WindowManager wm=new android.view.WindowManager();
 public Object getSystemService(String key){return wm;}
}
