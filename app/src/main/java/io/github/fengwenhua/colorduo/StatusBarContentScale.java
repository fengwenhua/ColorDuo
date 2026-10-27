/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;

/** Scale a complete side uniformly while preserving its physical edge and cutout allocation. */
final class StatusBarContentScale {
    private final WeakReference<View> view;
    private final boolean start;
    private boolean owned;
    private int width,height,gravity;
    private float scaleX,scaleY,pivotX,pivotY;
    private boolean pivotSet;

    StatusBarContentScale(View view,boolean start) { this.view=new WeakReference<>(view);this.start=start; }

    void apply(int percent) {
        View child=view.get();
        if(child==null || !(child.getParent() instanceof ViewGroup))return;
        if(percent<=Prefs.DEF_STATUS_BAR_HEIGHT) { restore();return; }
        ViewGroup parent=(ViewGroup)child.getParent();
        int roomWidth=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();
        int roomHeight=parent.getHeight()-parent.getPaddingTop()-parent.getPaddingBottom();
        ViewGroup.LayoutParams raw=child.getLayoutParams();
        if(!(raw instanceof FrameLayout.LayoutParams) || roomWidth<=0 || roomHeight<=0)return;
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;
        if(!owned) {
            width=params.width;height=params.height;gravity=params.gravity;
            scaleX=child.getScaleX();scaleY=child.getScaleY();
            pivotSet=child.isPivotSet();pivotX=child.getPivotX();pivotY=child.getPivotY();owned=true;
        }
        float scale=percent/100f;
        int targetWidth=Math.max(1,(int)(roomWidth/scale));
        int targetHeight=Math.max(1,(int)(roomHeight/scale));
        int targetGravity=(start?Gravity.START:Gravity.END)|Gravity.CENTER_VERTICAL;
        if(params.width!=targetWidth || params.height!=targetHeight || params.gravity!=targetGravity) {
            params.width=targetWidth;params.height=targetHeight;params.gravity=targetGravity;
            child.setLayoutParams(params);
        }
        boolean left=start!=(child.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL);
        child.setPivotX(left?0:targetWidth);child.setPivotY(targetHeight/2f);
        child.setScaleX(scaleX*scale);child.setScaleY(scaleY*scale);
    }

    void restore() {
        View child=view.get();if(!owned || child==null)return;owned=false;
        ViewGroup.LayoutParams raw=child.getLayoutParams();
        if(raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;
            if(params.width!=width || params.height!=height || params.gravity!=gravity) {
                params.width=width;params.height=height;params.gravity=gravity;child.setLayoutParams(params);
            }
        }
        child.setScaleX(scaleX);child.setScaleY(scaleY);
        if(pivotSet) { child.setPivotX(pivotX);child.setPivotY(pivotY); }else child.resetPivot();
    }

    String status() {
        View child=view.get();if(child==null)return "missing";
        int[] at=new int[2];child.getLocationOnScreen(at);
        return child.getWidth()+"x"+child.getHeight()+" scale="+child.getScaleX()+","+child.getScaleY()
                +" centreY="+(at[1]+child.getHeight()*child.getScaleY()/2f);
    }
}
