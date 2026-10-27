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
    private WeakReference<View> endChild=new WeakReference<>(null);
    private int endGravity;
    private boolean endOwned;

    StatusBarContentScale(View view,boolean start) { this.view=new WeakReference<>(view);this.start=start; }

    boolean apply(int percent) {
        View child=view.get();
        if(child==null || !(child.getParent() instanceof ViewGroup))return false;
        if(percent<=Prefs.DEF_STATUS_BAR_HEIGHT)return restore();
        ViewGroup parent=(ViewGroup)child.getParent();
        int roomWidth=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();
        int roomHeight=parent.getHeight()-parent.getPaddingTop()-parent.getPaddingBottom();
        ViewGroup.LayoutParams raw=child.getLayoutParams();
        if(!(raw instanceof FrameLayout.LayoutParams) || roomWidth<=0 || roomHeight<=0)return false;
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
        boolean layoutChanged=false;
        if(params.width!=targetWidth || params.height!=targetHeight || params.gravity!=targetGravity) {
            params.width=targetWidth;params.height=targetHeight;params.gravity=targetGravity;
            child.setLayoutParams(params);
            layoutChanged=true;
        }
        if(!start && child instanceof ViewGroup) {
            int id=child.getResources().getIdentifier("status_bar_end_side_content","id","com.android.systemui");
            View inner=id==0?null:child.findViewById(id);
            if(inner!=null && inner.getParent()==child && inner.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams innerParams=(FrameLayout.LayoutParams)inner.getLayoutParams();
                if(!endOwned) { endChild=new WeakReference<>(inner);endGravity=innerParams.gravity;endOwned=true; }
                int vertical=endGravity<0?Gravity.CENTER_VERTICAL:(endGravity&Gravity.VERTICAL_GRAVITY_MASK);
                int aligned=Gravity.END|vertical;
                if(innerParams.gravity!=aligned) { innerParams.gravity=aligned;inner.setLayoutParams(innerParams);layoutChanged=true; }
            }
        }
        boolean left=start!=(child.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL);
        child.setPivotX(left?0:targetWidth);child.setPivotY(targetHeight/2f);
        child.setScaleX(scaleX*scale);child.setScaleY(scaleY*scale);
        return layoutChanged;
    }

    boolean restore() {
        View child=view.get();if(!owned || child==null)return false;owned=false;
        boolean layoutChanged=false;
        ViewGroup.LayoutParams raw=child.getLayoutParams();
        if(raw instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;
            if(params.width!=width || params.height!=height || params.gravity!=gravity) {
                params.width=width;params.height=height;params.gravity=gravity;child.setLayoutParams(params);
                layoutChanged=true;
            }
        }
        child.setScaleX(scaleX);child.setScaleY(scaleY);
        if(pivotSet) { child.setPivotX(pivotX);child.setPivotY(pivotY); }else child.resetPivot();
        View inner=endChild.get();
        if(endOwned && inner!=null && inner.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)inner.getLayoutParams();
            if(params.gravity!=endGravity) { params.gravity=endGravity;inner.setLayoutParams(params);layoutChanged=true; }
        }
        endOwned=false;endChild.clear();
        return layoutChanged;
    }

    String status() {
        View child=view.get();if(child==null)return "missing";
        int[] at=new int[2];child.getLocationOnScreen(at);
        return child.getWidth()+"x"+child.getHeight()+" scale="+child.getScaleX()+","+child.getScaleY()
                +" x="+at[0]+".."+(at[0]+child.getWidth()*child.getScaleX())
                +" centreY="+(at[1]+child.getHeight()*child.getScaleY()/2f);
    }
}
