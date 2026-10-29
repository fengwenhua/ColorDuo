/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Preserve the custom status bar transform through ColorOS's native mirror handoff. */
final class ColorOsShadeScale {
    private static final String WIDGET="com.oplus.systemui.seamless.widget.";
    private final XposedModule module;
    private final ClassLoader loader;
    private final IntSupplier percent;
    private final Predicate<View> scaledSource;
    private final Map<View,Boolean> mirrors=new WeakHashMap<>(),headers=new WeakHashMap<>();
    private record ClockMotion(float progress,float nativeScale) {}
    private final Map<View,ClockMotion> clockProgress=new WeakHashMap<>();
    private final Map<View,Part> parts=new WeakHashMap<>();
    private final Map<ViewGroup,boolean[]> clips=new WeakHashMap<>();
    private final ThreadLocal<Integer> drawing=ThreadLocal.withInitial(()->0);
    private Class<?> clock,notification,system;
    private Method source,offset,reset;
    private Constructor<?> pair;
    private boolean ready;
    private String error="None";
    private long draws;
    private float lastScale=1;

    private static final class Part {
        final WeakReference<View> view;
        final boolean end;
        float nativeX,nativeY,pivotX,pivotY;
        boolean pivotSet,owned;
        Part(View v,boolean end) { view=new WeakReference<>(v);this.end=end;nativeX=v.getScaleX();nativeY=v.getScaleY(); }
        void apply(float scale) {
            View v=view.get();if(v==null)return;
            if(scale==1) {
                if(!owned)return;
                v.setScaleX(nativeX);v.setScaleY(nativeY);
                if(pivotSet) { v.setPivotX(pivotX*v.getWidth());v.setPivotY(pivotY*v.getHeight()); }
                else v.resetPivot();
                owned=false;return;
            }
            if(v.getWidth()==0||v.getHeight()==0)return;
            if(!owned) {
                pivotSet=v.isPivotSet();pivotX=v.getPivotX()/v.getWidth();pivotY=v.getPivotY()/v.getHeight();owned=true;
            }
            v.setPivotX(end!=(v.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)?v.getWidth():0);
            v.setPivotY(v.getHeight()/2f);v.setScaleX(nativeX*scale);v.setScaleY(nativeY*scale);
        }
    }

    ColorOsShadeScale(XposedModule module,ClassLoader loader,IntSupplier percent,Predicate<View> scaledSource) {
        this.module=module;this.loader=loader;this.percent=percent;this.scaledSource=scaledSource;
    }
    private Class<?> type(String name)throws ClassNotFoundException { return Class.forName(name,false,loader); }
    private interface Around { Object run(XposedInterface.Chain chain)throws Throwable; }
    private void hook(Method method,Around action) {
        method.setAccessible(true);
        module.hook(method).setId("colorduo-shade-"+method.toGenericString())
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(action::run);
    }

    void install() {
        try {
            Class<?> frame=type(WIDGET+"StatusBarFakeFrame");clock=type(WIDGET+"ClockFakeFrame");
            notification=type(WIDGET+"NotificationFakeFrame");system=type(WIDGET+"SystemIconFakeFrame");
            source=frame.getDeclaredMethod("getMView");offset=notification.getDeclaredMethod("getOffsetX");
            reset=frame.getDeclaredMethod("resetLayoutParams");
            pair=type("kotlin.Pair").getConstructor(Object.class,Object.class);
            for(Class<?> owner:new Class<?>[]{frame,notification,system}) {
                hook(owner.getDeclaredMethod("dispatchDraw",Canvas.class),chain->{
                    View fake=(View)chain.getThisObject();View real=real(fake);float scale=scale(real);
                    if(scale==1||drawing.get()!=0)return chain.proceed();
                    mirrors.put(fake,true);
                    int kind=kind(fake);
                    ClockMotion motion=clockProgress.get(fake);
                    if(clock.isInstance(fake)&&motion!=null)scale=ShadeMirrorGeometry.clockScale(scale,motion.progress(),motion.nativeScale());
                    Canvas canvas=(Canvas)chain.getArg(0);
                    int save=ShadeMirrorGeometry.save(canvas,kind,fake.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,
                            fake.getWidth(),fake.getHeight(),real.getWidth(),real.getHeight(),
                            kind==ShadeMirrorGeometry.NOTIFICATIONS?((Number)offset.invoke(fake)).intValue():0,scale);
                    drawing.set(1);draws++;lastScale=scale;
                    try { return chain.proceed(); }finally { drawing.set(0);canvas.restoreToCount(save); }
                });
                hook(owner.getDeclaredMethod("resetLayoutParams"),chain->{
                    View fake=(View)chain.getThisObject();View real=real(fake);float scale=scale(real);
                    if(scale==1||real.getWidth()==0||real.getHeight()==0)return chain.proceed();
                    resize(fake,real,scale);return null;
                });
            }
            hook(frame.getDeclaredMethod("getRealStatusElementParams"),chain->{
                View real=real((View)chain.getThisObject());float scale=scale(real);
                return scale==1?chain.proceed():pair.newInstance(ShadeMirrorGeometry.pixels(real.getWidth(),scale),
                        ShadeMirrorGeometry.pixels(real.getHeight(),scale));
            });
            for(Class<?> owner:new Class<?>[]{frame,clock,notification}) {
                hook(owner.getDeclaredMethod("getRealStatusElementLocX"),chain->coordinate(chain,0));
            }
            hook(frame.getDeclaredMethod("getRealStatusElementLocY"),chain->coordinate(chain,1));
            Class<?> header=type("com.oplus.systemui.qs.OplusQuickStatusBarHeader");
            for(Method method:header.getDeclaredMethods())if(method.getName().equals("onFinishInflate")
                    ||method.getName().equals("updateResources")||method.getName().equals("onLayout"))hook(method,chain->{
                Object result=chain.proceed();if(ready)trackHeader((View)chain.getThisObject());return result;
            });
            installControllers();ready=true;
        }catch(ReflectiveOperationException|RuntimeException failure) { fail(failure); }
    }

    private void installControllers()throws ReflectiveOperationException {
        Class<?> simple=type("com.oplus.systemui.separate.OplusSimpleQSFakeController");
        Field simpleClock=simple.getDeclaredField("fakeClockViewContainer");simpleClock.setAccessible(true);
        hook(simple.getDeclaredMethod("updateFakeClockScale",float.class),chain->{
            Object result=chain.proceed();ViewGroup container=(ViewGroup)simpleClock.get(chain.getThisObject());
            if(container!=null)for(int i=0;i<container.getChildCount();i++)if(clock.isInstance(container.getChildAt(i)))
                progress(container.getChildAt(i),((Number)chain.getArg(0)).floatValue(),container.getScaleX());
            return result;
        });
        Class<?> listener=type("com.oplus.systemui.separate.OplusSimpleQSFakeController$systemIconsScaleChangedListener$1");
        Field outer=listener.getDeclaredField("this$0");outer.setAccessible(true);
        Method icons=simple.getDeclaredMethod("getStatusIcons");
        hook(listener.getDeclaredMethod("onScaleChanged",float.class),chain->{
            Object result=chain.proceed();nativeScale((View)icons.invoke(outer.get(chain.getThisObject())),((Number)chain.getArg(0)).floatValue());return result;
        });
        Class<?> separate=type("com.oplus.systemui.plugins.qs.seamless.SeparateQSFakeStatusController");
        Field separateClock=separate.getDeclaredField("qsFakeClock");separateClock.setAccessible(true);
        hook(separate.getDeclaredMethod("updateLeftAreaState",float.class),chain->{
            Object result=chain.proceed();Object fake=separateClock.get(chain.getThisObject());
            if(fake instanceof View)progress((View)fake,((Number)chain.getArg(0)).floatValue(),((View)fake).getScaleX());return result;
        });
        Field separateHeader=separate.getDeclaredField("statusBarHeader");separateHeader.setAccessible(true);
        Method status=separateHeader.getType().getMethod("getQsStatusIconsView");
        hook(separate.getDeclaredMethod("onSystemIconScaleChanged",float.class),chain->{
            Object result=chain.proceed();nativeScale((View)status.invoke(separateHeader.get(chain.getThisObject())),((Number)chain.getArg(0)).floatValue());return result;
        });
    }
    private void progress(View fake,float value,float nativeScale) { clockProgress.put(fake,new ClockMotion(value,nativeScale));fake.invalidate(); }
    private void nativeScale(View view,float value) {
        Part part=parts.get(view);if(part!=null) { part.nativeX=value;part.nativeY=value;part.apply(current()); }
    }
    private float current() { return ready?percent.getAsInt()/100f:1f; }
    private float scale(View real) { return real!=null&&ready&&scaledSource.test(real)?current():1f; }
    private View real(View fake)throws ReflectiveOperationException { return source==null?null:(View)source.invoke(fake); }
    private int kind(View fake) {
        return system.isInstance(fake)?ShadeMirrorGeometry.SYSTEM:notification.isInstance(fake)?ShadeMirrorGeometry.NOTIFICATIONS:ShadeMirrorGeometry.CLOCK;
    }
    private Object coordinate(XposedInterface.Chain chain,int axis)throws Throwable {
        View real=real((View)chain.getThisObject());if(scale(real)==1)return chain.proceed();
        int[] at=new int[2];real.getLocationOnScreen(at);return (float)at[axis];
    }
    private void resize(View fake,View real,float scale) {
        mirrors.put(fake,true);
        ViewGroup.LayoutParams raw=fake.getLayoutParams();if(!(raw instanceof FrameLayout.LayoutParams))return;
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)raw;
        int width=kind(fake)==ShadeMirrorGeometry.CLOCK?ShadeMirrorGeometry.pixels(real.getWidth(),scale):ViewGroup.LayoutParams.MATCH_PARENT;
        int height=ShadeMirrorGeometry.pixels(real.getHeight(),scale);
        if(lp.width!=width||lp.height!=height||lp.gravity!=16) { lp.width=width;lp.height=height;lp.gravity=16;fake.setLayoutParams(lp); }
        fake.invalidate();
    }
    private void trackHeader(View header) {
        if(!headers.containsKey(header)) {
            headers.put(header,true);addPart(header,"qs_clock_container",false);addPart(header,"quick_qs_status_icons",true);
            ViewTreeObserver.OnPreDrawListener before=()->{applyHeader(header);return true;};
            header.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { v.getViewTreeObserver().addOnPreDrawListener(before); }
                @Override public void onViewDetachedFromWindow(View v) { if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(before); }
            });
            if(header.isAttachedToWindow())header.getViewTreeObserver().addOnPreDrawListener(before);
        }
        applyHeader(header);
    }
    private void addPart(View header,String name,boolean end) {
        int id=header.getResources().getIdentifier(name,"id","com.android.systemui");View view=id==0?null:header.findViewById(id);
        if(view!=null&&!parts.containsKey(view))parts.put(view,new Part(view,end));
    }
    private void applyHeader(View header) {
        float scale=current();
        for(Map.Entry<View,Part> entry:new ArrayList<>(parts.entrySet()))if(entry.getKey().getParent()==header)entry.getValue().apply(scale);
        if(scale!=1) {
            View group=header;
            for(int i=0;i<2&&group instanceof ViewGroup;i++) {
                ViewGroup parent=(ViewGroup)group;
                if(!clips.containsKey(parent))clips.put(parent,new boolean[]{parent.getClipChildren(),parent.getClipToPadding()});
                parent.setClipChildren(false);parent.setClipToPadding(false);
                group=parent.getParent() instanceof View?(View)parent.getParent():null;
            }
        }
    }
    void refresh() {
        for(View header:new ArrayList<>(headers.keySet()))applyHeader(header);
        for(View fake:new ArrayList<>(mirrors.keySet()))try {
            View real=real(fake);float scale=scale(real);
            if(real!=null&&scale!=1)resize(fake,real,scale);else reset.invoke(fake);
        }catch(ReflectiveOperationException|RuntimeException failure) { fail(failure);break; }
        if(current()==1) {
            for(Map.Entry<ViewGroup,boolean[]> e:new ArrayList<>(clips.entrySet())) {
                e.getKey().setClipChildren(e.getValue()[0]);e.getKey().setClipToPadding(e.getValue()[1]);
            }
            clips.clear();
        }
    }
    private void fail(Throwable failure) {
        ready=false;error=failure.getClass().getSimpleName();
        module.log(5,"ColorDuoShadeScale","Native fallback: "+error);
    }
    String status() {
        return "ready="+ready+" headers="+headers.size()+" mirrors="+mirrors.size()+" draws="+draws+" lastScale="+lastScale+" error="+error;
    }
}
