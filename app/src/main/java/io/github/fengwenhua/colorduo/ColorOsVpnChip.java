/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** The separate QS header renders VPN inside its privacy chip, outside StatusIconContainer. */
final class ColorOsVpnChip {
    private static final String BINDER = "com.oplus.systemui.plugins.qs.privacy.ui.binder.OplusQSPrivacyContainerViewBinder";
    private final Map<View, Boolean> meters = new WeakHashMap<>();
    private final Map<ViewGroup, Snapshot> rows = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Method fadeIn, vpnMode, getShowSecurity, getValue, notifyShowing;
    private boolean queued;
    private String error = "None";
    private static final class Snapshot {
        final Object binder, security;
        final List<?> icons;
        boolean suppressed;
        Snapshot(Object binder, Object security, List<?> icons) {
            this.binder=binder; this.security=security; this.icons=new ArrayList<>(icons);
        }
    }

    void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> binder = Class.forName(BINDER, false, loader);
            Class<?> security = Class.forName("com.android.systemui.plugins.qs.QSSecurity", false, loader);
            vpnMode = security.getMethod("isVpnMode");
            getShowSecurity = security.getMethod("getShowSecurity");
            getValue = getShowSecurity.getReturnType().getMethod("get");
            fadeIn = binder.getDeclaredMethod("fadeInNewIcons", ViewGroup.class, Context.class, List.class, boolean.class, security);
            notifyShowing = binder.getMethod("setPrivacyElementShowing", boolean.class);
            module.hook(fadeIn).setId("colorduo-coloros-vpn-chip")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        ViewGroup row = (ViewGroup) chain.getArg(0);
                        Snapshot state = new Snapshot(chain.getThisObject(), chain.getArg(4), (List<?>) chain.getArg(2));
                        boolean hide = false;
                        try {
                            hide = Boolean.TRUE.equals(chain.getArg(3)) && represented(row)
                                    && Boolean.TRUE.equals(vpnMode.invoke(state.security));
                        } catch (ReflectiveOperationException | RuntimeException failure) { error=failure.getClass().getSimpleName(); }
                        state.suppressed=hide;
                        rows.put(row,state);
                        Object result = hide ? chain.proceed(new Object[]{row, chain.getArg(1), chain.getArg(2), false, state.security}) : chain.proceed();
                        // The OEM method assumes at least one element. Avoid leaving an empty pill.
                        if ((hide || !Boolean.TRUE.equals(chain.getArg(3))) && state.icons.isEmpty()) collapse(row,state);
                        if (!meters.isEmpty()) schedule();
                        return result;
                    });
            // The QS plugin owns its Kotlin callback class in a separate class loader.
            // Resolve the observed method from its declaring class, not SystemUI's loader.
            Method fadeOut=null;
            for(Method method:binder.getDeclaredMethods()) {
                if(method.getName().equals("fadeOutOldIcons") && method.getParameterCount()==2
                        && method.getParameterTypes()[0]==ViewGroup.class) {
                    if(fadeOut!=null)throw new NoSuchMethodException("Ambiguous privacy fade-out");
                    fadeOut=method;
                }
            }
            if(fadeOut==null)throw new NoSuchMethodException("Privacy fade-out unavailable");
            module.hook(fadeOut).setId("colorduo-coloros-vpn-chip-expire")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        // The native model is changing; never replay stale microphone/camera items.
                        rows.remove((ViewGroup) chain.getArg(0));
                        return chain.proceed();
                    });
        } catch (ReflectiveOperationException | RuntimeException failure) { error=failure.getClass().getSimpleName(); }
    }

    void commit(View meter, boolean indicatorDrawn) {
        boolean before=meters.containsKey(meter);
        if(indicatorDrawn)meters.put(meter,true);else meters.remove(meter);
        if(before!=indicatorDrawn) schedule();
    }
    void release(View meter) { if(meters.remove(meter)!=null)schedule(); }
    String status() {
        int hidden=0;for(Snapshot state:rows.values())if(state.suppressed)hidden++;
        return "rows="+rows.size()+" hidden="+hidden+" error="+error;
    }

    private boolean represented(View row) {
        for(View meter:new ArrayList<>(meters.keySet())) {
            if(!meter.isAttachedToWindow() || !meter.isShown() || meter.getWindowToken()!=row.getWindowToken())continue;
            ViewParent ancestor=meter.getParent();
            for(int depth=0;depth<6 && ancestor instanceof View;depth++,ancestor=ancestor.getParent()) {
                if(ancestor==meter.getRootView())break;
                for(ViewParent parent=row.getParent();parent!=null;parent=parent.getParent())if(parent==ancestor)return true;
            }
        }
        return false;
    }

    private void schedule() {
        if(queued)return;queued=true;
        main.post(() -> {
            queued=false;
            for(ViewGroup row:new ArrayList<>(rows.keySet())) {
                Snapshot state=rows.get(row);
                if(state==null || !row.isAttachedToWindow() || !(row.getParent() instanceof View)
                        || !((View)row.getParent()).isShown())continue;
                try {
                    boolean visible=Boolean.TRUE.equals(getValue.invoke(getShowSecurity.invoke(state.security)));
                    boolean hide=visible && represented(row) && Boolean.TRUE.equals(vpnMode.invoke(state.security));
                    if(hide!=state.suppressed)
                        fadeIn.invoke(state.binder,row,row.getContext(),state.icons,visible,state.security);
                } catch (ReflectiveOperationException | RuntimeException failure) { error=failure.getClass().getSimpleName(); }
            }
        });
    }

    private void collapse(ViewGroup row, Snapshot state) {
        row.animate().cancel();
        row.setVisibility(View.GONE);
        ViewGroup.LayoutParams layout=row.getLayoutParams();
        if(layout!=null && layout.width!=0) { layout.width=0;row.setLayoutParams(layout); }
        try { notifyShowing.invoke(state.binder,false); }
        catch(ReflectiveOperationException failure) { error=failure.getClass().getSimpleName(); }
    }
}
