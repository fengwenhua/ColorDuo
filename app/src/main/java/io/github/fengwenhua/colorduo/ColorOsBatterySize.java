/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Own image bounds and an optional meter height, restoring OEM values before style updates. */
final class ColorOsBatterySize {
    private final WeakReference<ImageView> image;
    private boolean owned;
    private int nativeWidth, nativeHeight, appliedWidth, appliedHeight;
    private WeakReference<View> meterOwner;
    private boolean meterOwned;
    private int nativeMeterHeight, appliedMeterHeight;

    ColorOsBatterySize(ImageView image) { this.image = new WeakReference<>(image); }

    boolean resizeNeeded(View meter, TrioSettings settings) {
        return resizeNeeded(meter, settings, 0);
    }

    int[] baseSize(View meter, TrioSettings settings) {
        ImageView view = image.get();
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        if (params == null) return new int[]{0, 0};
        int width = owned ? nativeWidth : params.width, height = owned ? nativeHeight : params.height;
        if (width <= 0 || height <= 0 || meter.getHeight() <= 0) return new int[]{0, 0};
        int availableWidth=meter.getResources().getDisplayMetrics().widthPixels/3;
        if(settings.trioStyle==Prefs.STYLE_CAPSULE)
            return target(height,restoredAvailableHeight(meter,params),availableWidth,settings.capsuleLayout.length());
        if(enlargedRing(settings))return RingViewport.target(width,height,availableWidth,availableRingHeight(meter,params),settings.glyphScale);
        return new int[]{width,height};
    }

    boolean resizeNeeded(View meter, TrioSettings settings, int extraWidth) {
        ImageView view = image.get();
        if (view == null) return false;
        if (settings.trioStyle != Prefs.STYLE_CAPSULE && extraWidth == 0 && !enlargedRing(settings)) return owned;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return false;
        if (owned && (params.width != appliedWidth || params.height != appliedHeight))
            throw new IllegalStateException("battery size changed by another owner");
        int[] target = baseSize(meter, settings);
        if (target[1] <= 0) return false;
        target[0] += extraWidth;
        return params.width != target[0] || params.height != target[1];
    }

    void apply(View meter, TrioSettings settings) {
        apply(meter, settings, 0);
    }

    void apply(View meter, TrioSettings settings, int extraWidth) {
        if (settings.trioStyle != Prefs.STYLE_CAPSULE && extraWidth == 0 && !enlargedRing(settings)) { restore(); return; }
        ImageView view = image.get();
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height <= 0 || meter.getHeight() <= 0) return;
        int[] target = baseSize(meter, settings);
        if (!owned) { nativeWidth = params.width; nativeHeight = params.height; }
        if(enlargedRing(settings))applyMeterHeight(meter,target[1],params);
        else restoreMeterHeight(true);
        target[0] += extraWidth;
        appliedWidth = target[0]; appliedHeight = target[1]; owned = true;
        if (params.width != appliedWidth || params.height != appliedHeight) {
            params.width = appliedWidth; params.height = appliedHeight; view.setLayoutParams(params);
        }
    }

    /** Restore only the params the native helper reads; keep the current measured frame on screen. */
    void beforeNativeStyle() {
        restoreMeterHeight(false);
        if (!owned) return;
        ImageView view = image.get();
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        if (params != null) {
            if (params.width != appliedWidth || params.height != appliedHeight)
                throw new IllegalStateException("battery size changed by another owner");
            params.width = nativeWidth;
            params.height = nativeHeight;
        }
        owned = false;
    }

    void restore() {
        restoreMeterHeight(true);
        if (!owned) return;
        ImageView view = image.get();
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        if (params != null) {
            boolean changed = false;
            if (params.width == appliedWidth) { params.width = nativeWidth; changed = true; }
            if (params.height == appliedHeight) { params.height = nativeHeight; changed = true; }
            if (changed) view.setLayoutParams(params);
        }
        owned = false;
    }

    private static int availableHeight(View meter, ViewGroup.LayoutParams params) {
        int measured=meter.getHeight();
        ViewGroup.LayoutParams meterParams=meter.getLayoutParams();
        if(meterParams!=null&&meterParams.height>0)measured=Math.min(measured,meterParams.height);
        int height = measured - meter.getPaddingTop() - meter.getPaddingBottom();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            height -= margins.topMargin + margins.bottomMargin;
        }
        return height;
    }

    static boolean enlargedRing(TrioSettings settings) {
        return settings.trioStyle==Prefs.STYLE_RING&&settings.glyphScale>100;
    }

    private int restoredAvailableHeight(View meter,ViewGroup.LayoutParams imageParams) {
        int available=availableHeight(meter,imageParams);
        int base=meterOwned?nativeMeterHeight:meter.getLayoutParams()==null?0:meter.getLayoutParams().height;
        if(meterOwned&&base>0) {
            int inner=base-meter.getPaddingTop()-meter.getPaddingBottom();
            if(imageParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)imageParams;inner-=m.topMargin+m.bottomMargin;
            }
            available=Math.min(available,inner);
        } else if(base==ViewGroup.LayoutParams.WRAP_CONTENT&&owned&&appliedHeight>nativeHeight)available=Math.min(available,nativeHeight);
        return available;
    }

    String describe(View meter) {
        Object parent=meter.getParent();
        return "native="+nativeWidth+"x"+nativeHeight+" applied="+appliedWidth+"x"+appliedHeight
                +" meter="+meter.getHeight()+" rootOwned="+meterOwned
                +" parent="+(parent instanceof View?parent.getClass().getSimpleName()+":"+((View)parent).getHeight():"none");
    }

    private static int availableRingHeight(View meter,ViewGroup.LayoutParams imageParams) {
        if(!(meter.getParent() instanceof View))return availableHeight(meter,imageParams);
        View parent=(View)meter.getParent();
        int height=parent.getHeight()-parent.getPaddingTop()-parent.getPaddingBottom()
                -meter.getPaddingTop()-meter.getPaddingBottom();
        ViewGroup.LayoutParams meterParams=meter.getLayoutParams();
        if(meterParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)meterParams;
            height-=margins.topMargin+margins.bottomMargin;
        }
        if(imageParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)imageParams;
            height-=margins.topMargin+margins.bottomMargin;
        }
        return height>0?height:availableHeight(meter,imageParams);
    }

    private void applyMeterHeight(View meter,int imageHeight,ViewGroup.LayoutParams imageParams) {
        ViewGroup.LayoutParams params=meter.getLayoutParams();
        if(params==null)return;
        int base=meterOwned?nativeMeterHeight:params.height;
        if(base<=0)return; // MATCH_PARENT and WRAP_CONTENT already follow the enlarged child.
        if(meterOwned&&params.height!=appliedMeterHeight)throw new IllegalStateException("battery meter height changed by another owner");
        int wanted=imageHeight+meter.getPaddingTop()+meter.getPaddingBottom();
        if(imageParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)imageParams;
            wanted+=margins.topMargin+margins.bottomMargin;
        }
        wanted=Math.max(base,wanted);
        if(wanted==base&&!meterOwned)return;
        if(!meterOwned){nativeMeterHeight=base;meterOwner=new WeakReference<>(meter);}
        meterOwned=true;appliedMeterHeight=wanted;
        if(params.height!=wanted){params.height=wanted;meter.setLayoutParams(params);}
    }

    private void restoreMeterHeight(boolean requestLayout) {
        if(!meterOwned)return;
        View meter=meterOwner==null?null:meterOwner.get();
        ViewGroup.LayoutParams params=meter==null?null:meter.getLayoutParams();
        if(params!=null&&params.height==appliedMeterHeight) {
            params.height=nativeMeterHeight;
            if(requestLayout)meter.setLayoutParams(params);
        }
        meterOwned=false;meterOwner=null;
    }

    static int[] target(int baseHeight, int availableHeight, int availableWidth, int length) {
        if (baseHeight <= 0 || availableHeight <= 0 || availableWidth <= 0) throw new IllegalArgumentException("bounds");
        TrioGeometry.setCapsuleLength(length);
        int body = baseHeight;
        int width = TrioGeometry.capHostWidthPx(body);
        int height = TrioGeometry.capHostHeightPx(body);
        float fit = Math.min(1f, Math.min(availableWidth / (float) width, availableHeight / (float) height));
        body = Math.max(1, (int) Math.floor(body * fit));
        return new int[]{Math.max(1, TrioGeometry.capHostWidthPx(body)),
                Math.max(1, TrioGeometry.capHostHeightPx(body))};
    }
}
