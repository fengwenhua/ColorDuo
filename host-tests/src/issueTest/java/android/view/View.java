/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package android.view;
import android.content.Context;
public class View implements ViewParent {
 public static final int VISIBLE=0;
 public Context context; public View parent; public float alpha=1;
 public boolean shown=true,attached=true,unclipped=true;
 public int width=60,height=90,x=900,y=0,windowVisibility=0,invalidations;
 public WindowInsets insets=new WindowInsets(); public ViewGroup.LayoutParams lp; public Runnable delayed;
 public View(Context c){context=c;}
 public Context getContext(){return context;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public float getAlpha(){return alpha;} public void setAlpha(float a){alpha=a;}
 public void setVisibility(int v){shown=v==VISIBLE;} public boolean isShown(){return shown;}
 public int getWindowVisibility(){return windowVisibility;}
 public boolean isAttachedToWindow(){return attached;}
 public ViewParent getParent(){return parent;}
 public View getRootView(){return parent==null?this:parent.getRootView();}
 public ViewGroup.LayoutParams getLayoutParams(){return lp;}
 public WindowInsets getRootWindowInsets(){return insets;}
 public void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}
 public boolean getGlobalVisibleRect(android.graphics.Rect r){return unclipped;}
 public boolean postDelayed(Runnable r,long ms){delayed=r;return true;}
 public boolean removeCallbacks(Runnable r){return true;}
 public void invalidate(){invalidations++;}
 protected void onDraw(android.graphics.Canvas c){}
}
