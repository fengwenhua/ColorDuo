/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package android.view;
public class WindowManager {
 public int adds,updates,removes; public boolean failNext,failAdd; public View glyph;
 public void addView(View v,LayoutParams p){if(failAdd)throw new IllegalStateException("add");adds++;glyph=v;}
 public void updateViewLayout(View v,LayoutParams p){updates++;if(failNext){failNext=false;throw new IllegalStateException("placement");}}
 public void removeViewImmediate(View v){removes++;}
 public static class LayoutParams extends ViewGroup.LayoutParams {
  public static final int FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCHABLE=16,FLAG_LAYOUT_IN_SCREEN=256,FLAG_LAYOUT_NO_LIMITS=512;
  public static final int TYPE_STATUS_BAR_SUB_PANEL=2017,TYPE_STATUS_BAR_ADDITIONAL=2041,PRIVATE_FLAG_TRUSTED_OVERLAY=1<<29;
  public int type,gravity,width,height,format,flags,privateFlags,windowAnimations,x,y; public float alpha=1;
  public void setTitle(String s){}
 }
}
