/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Resize the native status-bar window, its provided insets and the local icon row together. */
final class ColorOsStatusBarHeight {
    private static final String PHONE_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static volatile int activePercent = Prefs.DEF_STATUS_BAR_HEIGHT;
    private final XposedModule module;
    private final ClassLoader loader;
    private final Supplier<TrioSettings> settings;
    private final Map<View, Boolean> views = new WeakHashMap<>();
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Method updateHeight, updateWindow;
    private boolean ready;
    private int applied = -1;
    private String error = "None";

    ColorOsStatusBarHeight(XposedModule module, ClassLoader loader, Supplier<TrioSettings> settings) {
        this.module=module; this.loader=loader; this.settings=settings;
    }

    void install() {
        try {
            Class<?> phone=Class.forName(PHONE_VIEW,false,loader);
            updateHeight=phone.getDeclaredMethod("updateStatusBarHeight");
            updateWindow=phone.getDeclaredMethod("updateWindowHeight");
            Class<?> utility=Class.forName("com.android.internal.policy.SystemBarUtils",false,loader);
            int hooks=0;
            for(Method method:utility.getDeclaredMethods()) {
                if(!Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=int.class
                        || !(method.getName().equals("getStatusBarHeight") || method.getName().equals("getStatusBarHeightForRotation")))continue;
                module.hook(method).setId("colorduo-status-bar-height-"+method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                            int nesting=depth.get();depth.set(nesting+1);
                            try {
                                int nativeHeight=(Integer)chain.proceed();
                                return nesting==0 && ready ? StatusBarHeightPolicy.scale(nativeHeight,activePercent) : nativeHeight;
                            } finally { depth.set(nesting); }
                        });
                hooks++;
            }
            if(hooks<2)throw new NoSuchMethodException("Status-bar height API incomplete");
            module.hook(updateHeight).setId("colorduo-status-bar-row-height")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object result=chain.proceed();
                        View view=(View)chain.getThisObject();views.put(view,true);
                        try { if(ready)scaleRow(view); }
                        catch(RuntimeException failure) { fail(failure); }
                        return result;
                    });
            ready=true;
        } catch(Throwable failure) { fail(failure); }
    }

    void refresh() {
        if(Looper.myLooper()!=Looper.getMainLooper()) { main.post(this::refresh);return; }
        int next=ready ? StatusBarHeightPolicy.percent(settings.get()) : Prefs.DEF_STATUS_BAR_HEIGHT;
        if(applied==next)return;
        applied=next;activePercent=next;
        for(View view:new ArrayList<>(views.keySet())) {
            if(!view.isAttachedToWindow())continue;
            try {
                // Both are native paths. updateWindow also updates all rotation-specific inset providers.
                updateHeight.invoke(view);updateWindow.invoke(view);view.requestApplyInsets();
            } catch(ReflectiveOperationException | RuntimeException failure) { fail(failure);restore();break; }
        }
    }

    private void scaleRow(View view) {
        int rowId=view.getResources().getIdentifier("system_icons","id","com.android.systemui");
        int sizeId=view.getResources().getIdentifier("status_bar_system_icons_height","dimen","com.android.systemui");
        View row=rowId==0 ? null : view.findViewById(rowId);
        if(row==null || sizeId==0)throw new IllegalStateException("Native status icon row unavailable");
        ViewGroup.LayoutParams params=row.getLayoutParams();
        if(params==null)return;
        int height=StatusBarHeightPolicy.scale(view.getResources().getDimensionPixelSize(sizeId),activePercent);
        if(params.height!=height) { params.height=height;row.setLayoutParams(params); }
    }

    private void fail(Throwable failure) {
        boolean restoreNeeded=ready;
        ready=false;activePercent=Prefs.DEF_STATUS_BAR_HEIGHT;error=failure.getClass().getSimpleName();
        if(restoreNeeded)main.post(this::restore);
        try { module.log(5,"ColorDuoStatusBarHeight","Native fallback: "+error); }catch(Throwable ignored){}
    }

    private void restore() {
        for(View view:new ArrayList<>(views.keySet()))try {
            if(view.isAttachedToWindow()) { updateHeight.invoke(view);updateWindow.invoke(view); }
        } catch(ReflectiveOperationException | RuntimeException ignored){}
    }

    static int percentFor(View meter) {
        if(activePercent==Prefs.DEF_STATUS_BAR_HEIGHT)return activePercent;
        for(ViewParent parent=meter.getParent();parent instanceof View;parent=parent.getParent())
            if(parent.getClass().getName().equals(PHONE_VIEW))return activePercent;
        return Prefs.DEF_STATUS_BAR_HEIGHT;
    }

    String status() { return "ready="+ready+" percent="+activePercent+" views="+views.size()+" error="+error; }
}
