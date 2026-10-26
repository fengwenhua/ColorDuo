/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package android.graphics;
public class Rect {
 public int left,top,right,bottom;
 public Rect(){} public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}
 public boolean isEmpty(){return left>=right || top>=bottom;}
}
