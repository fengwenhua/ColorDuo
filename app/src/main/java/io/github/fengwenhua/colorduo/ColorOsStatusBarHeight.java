/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.function.IntSupplier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Coordinate the native window/insets, camera-aligned content band and both complete icon groups. */
final class ColorOsStatusBarHeight {
    private static final String PHONE_VIEW="com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private final XposedModule module;
    private final ClassLoader loader;
    private final Supplier<TrioSettings> settings;
    private final Map<View,Row> views=new WeakHashMap<>();
    private final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
    private final ThreadLocal<Integer> nativePadding=ThreadLocal.withInitial(()->0);
    private final Handler main=new Handler(Looper.getMainLooper());
    private Method updateHeight,updateWindow,updatePadding;
    private Field extensionView;
    private volatile boolean ready;
    private volatile int percent=Prefs.DEF_STATUS_BAR_HEIGHT;
    private int applied=-1;
    private boolean queued;
    private String error="None";
    private int nativeHeight,paddingTop,paddingBottom;
    private final ColorOsShadeScale shade;

    private static final class Row {
        WeakReference<Object> extension=new WeakReference<>(null);
        final StatusBarContentScale left,right;
        Row(View view,Runnable changed,IntSupplier currentPercent) {
            // The inner EndSideContentLayout sits in a WRAP_CONTENT fake container.
            // Scale the fake container against its stable outer allocation instead.
            View start=find(view,"status_bar_start_side_content"),end=find(view,"status_bar_end_side_container_for_fake");
            if(start==null || end==null)throw new IllegalStateException("Status bar sides unavailable");
            left=new StatusBarContentScale(start,true);right=new StatusBarContentScale(end,false);
            ((View)start.getParent()).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed.run());
            ((View)end.getParent()).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed.run());
            ViewTreeObserver.OnPreDrawListener beforeDraw=()->!apply(currentPercent.getAsInt());
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { v.getViewTreeObserver().addOnPreDrawListener(beforeDraw); }
                @Override public void onViewDetachedFromWindow(View v) {
                    if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(beforeDraw);
                }
            });
            if(view.isAttachedToWindow())view.getViewTreeObserver().addOnPreDrawListener(beforeDraw);
        }
        void restore() { left.restore();right.restore(); }
        boolean apply(int percent) { boolean changed=left.apply(percent);return right.apply(percent)||changed; }
    }

    ColorOsStatusBarHeight(XposedModule module,ClassLoader loader,Supplier<TrioSettings> settings) {
        this.module=module;this.loader=loader;this.settings=settings;
        shade=new ColorOsShadeScale(module,loader,()->ready?percent:Prefs.DEF_STATUS_BAR_HEIGHT,source->{
            for(Row row:views.values())if(row.left.contains(source)||row.right.contains(source))return true;
            return false;
        });
    }

    void install() {
        try {
            Class<?> phone=Class.forName(PHONE_VIEW,false,loader);
            updateHeight=phone.getDeclaredMethod("updateStatusBarHeight");
            updateWindow=phone.getDeclaredMethod("updateWindowHeight");
            Class<?> extension=Class.forName("com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl",false,loader);
            updatePadding=extension.getDeclaredMethod("updateContentsPadding");
            extensionView=extension.getDeclaredField("phoneStatusBarView");extensionView.setAccessible(true);
            Class<?> provider=Class.forName("com.oplus.systemui.statusbar.phone.StatusBarPaddingProvider",false,loader);
            Field baseHeight=provider.getField("statusBarHeight"),holeTop=provider.getField("screenHoleTop"),holeBottom=provider.getField("screenHoleBottom");
            Method resources=provider.getDeclaredMethod("updateResources$16");
            Method homePadding=null;
            for(Method method:provider.getDeclaredMethods())if(method.getName().equals("getHomeStatusBarLeftRightPadding")
                    && method.getParameterCount()==3 && method.getReturnType()==Rect.class)homePadding=method;
            if(homePadding==null)throw new NoSuchMethodException("Home padding unavailable");

            module.hook(resources).setId("colorduo-status-bar-padding-baseline")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain->{
                        int nesting=nativePadding.get();nativePadding.set(nesting+1);
                        try { return chain.proceed(); }finally { nativePadding.set(nesting); }
                    });
            module.hook(homePadding).setId("colorduo-status-bar-camera-alignment")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain->{
                        Rect original=(Rect)chain.proceed();
                        if(!ready || percent==Prefs.DEF_STATUS_BAR_HEIGHT)return original;
                        try {
                            Object owner=chain.getThisObject();nativeHeight=baseHeight.getInt(owner);
                            int[] inset=StatusBarHeightPolicy.padding(nativeHeight,original.top,original.bottom,
                                    holeTop.getInt(owner),holeBottom.getInt(owner),percent);
                            paddingTop=inset[0];paddingBottom=inset[1];
                            return new Rect(original.left,inset[0],original.right,inset[1]);
                        }catch(ReflectiveOperationException | RuntimeException failure) { fail(failure);return original; }
                    });

            Class<?> utility=Class.forName("com.android.internal.policy.SystemBarUtils",false,loader);
            int hooks=0;
            for(Method method:utility.getDeclaredMethods()) {
                if(!Modifier.isStatic(method.getModifiers()) || method.getReturnType()!=int.class
                        || !(method.getName().equals("getStatusBarHeight") || method.getName().equals("getStatusBarHeightForRotation")))continue;
                module.hook(method).setId("colorduo-status-bar-height-"+method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain->{
                            int nesting=depth.get();depth.set(nesting+1);
                            try {
                                int original=(Integer)chain.proceed();
                                return nesting==0 && nativePadding.get()==0 && ready ? StatusBarHeightPolicy.scale(original,percent):original;
                            }finally { depth.set(nesting); }
                        });
                hooks++;
            }
            if(hooks<2)throw new NoSuchMethodException("Height API incomplete");
            module.hook(updateHeight).setId("colorduo-status-bar-track-view")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain->{
                        Object result=chain.proceed();
                        try { track((View)chain.getThisObject());schedule(); }catch(RuntimeException failure) { fail(failure); }
                        return result;
                    });
            module.hook(updatePadding).setId("colorduo-status-bar-refresh-padding")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain->{
                        try {
                            Object owner=chain.getThisObject();View view=(View)extensionView.get(owner);
                            if(view!=null) { Row row=track(view);row.extension=new WeakReference<>(owner); }
                        }catch(ReflectiveOperationException | RuntimeException failure) { fail(failure); }
                        Object result=chain.proceed();schedule();return result;
                    });
            ready=true;shade.install();
        }catch(Throwable failure) { fail(failure); }
    }

    private Row track(View view) {
        Row row=views.get(view);
        if(row==null) { row=new Row(view,this::schedule,()->ready?percent:Prefs.DEF_STATUS_BAR_HEIGHT);views.put(view,row); }
        return row;
    }

    void refresh() {
        if(Looper.myLooper()!=Looper.getMainLooper()) { main.post(this::refresh);return; }
        int next=ready?StatusBarHeightPolicy.percent(settings.get()):Prefs.DEF_STATUS_BAR_HEIGHT;
        if(applied==next)return;
        applied=next;percent=next;
        for(View view:new ArrayList<>(views.keySet())) {
            if(!view.isAttachedToWindow())continue;
            try {
                Row row=views.get(view);
                if(next==Prefs.DEF_STATUS_BAR_HEIGHT)row.restore();
                updateHeight.invoke(view);
                Object extension=row.extension.get();
                if(extension!=null)updatePadding.invoke(extension);
                updateWindow.invoke(view);view.requestApplyInsets();
            }catch(ReflectiveOperationException | RuntimeException failure) { fail(failure);break; }
        }
        shade.refresh();schedule();
    }

    private void schedule() {
        if(queued)return;queued=true;
        main.post(()->{
            queued=false;
            for(View view:new ArrayList<>(views.keySet()))try {
                if(view.isAttachedToWindow())views.get(view).apply(ready?percent:Prefs.DEF_STATUS_BAR_HEIGHT);
            }catch(RuntimeException failure) { fail(failure); }
            shade.refresh();
        });
    }

    private void fail(Throwable failure) {
        boolean restoreNeeded=ready;ready=false;percent=Prefs.DEF_STATUS_BAR_HEIGHT;error=failure.getClass().getSimpleName();
        if(restoreNeeded)main.post(()->{
            for(View view:new ArrayList<>(views.keySet()))try {
                Row row=views.get(view);row.restore();updateHeight.invoke(view);
                Object extension=row.extension.get();if(extension!=null)updatePadding.invoke(extension);
                updateWindow.invoke(view);
            }catch(ReflectiveOperationException | RuntimeException ignored){}
            shade.refresh();
        });
        try { module.log(5,"ColorDuoStatusBarHeight","Native fallback: "+error); }catch(Throwable ignored){}
    }

    private static View find(View root,String name) {
        int id=root.getResources().getIdentifier(name,"id","com.android.systemui");
        return id==0?null:root.findViewById(id);
    }
    String status() {
        String sides="";
        for(Row row:views.values()) { sides=" left={"+row.left.status()+"} right={"+row.right.status()+"}";break; }
        return "ready="+ready+" percent="+percent+" views="+views.size()+" nativeHeight="+nativeHeight
                +" padding="+paddingTop+","+paddingBottom+" error="+error+sides+" shade={"+shade.status()+"}";
    }
}
