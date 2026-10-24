param([string]$SourceDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$trio=Join-Path $root 'app/src/main/java/io/github/fengwenhua/colorduo'
if($SourceDirectory){$trio=$SourceDirectory}
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$src=Join-Path $root 'work/os4-layout/src'
$out=Join-Path $root 'work/os4-layout/classes'
$files=[Collections.Generic.List[string]]::new()
function Write-Source($name,$body){
    $path=Join-Path $src $name
    New-Item -ItemType Directory -Force (Split-Path $path -Parent) | Out-Null
    [IO.File]::WriteAllText($path,$body,[Text.UTF8Encoding]::new($false))
    $files.Add($path)
}
New-Item -ItemType Directory -Force $out | Out-Null
$hooks=[IO.File]::ReadAllText((Join-Path $trio 'TrioHooks.java'))
$methods=@(
    'private static void layoutOutRingStrip(ViewGroup container, View anchor)',
    'private static OutRingLayout.Plan outRingPlan(ViewGroup container, View icons)',
    'private static void requestOutRingReserve(final ViewGroup container)',
    'private static int outRingSlotWidth(View view)',
    'private static int islandBoundaryPx(View iconContainer)',
    'private static View iconContainerIn(View batteryContainer)',
    'private static void reserveOutTypeSpace(ViewGroup batteryContainer, int reserve)',
    'private static void releaseOutTypeSpace(View batteryContainer)',
    'private static void reserveOutRingStrip(ViewGroup container)',
    # The arrangement: which view is in which slot, and which slot is sacrificed first.
    # Spliced alongside the plan because they are the same decision - the plan's arrays are
    # indexed from the battery outwards, so it is these three that decide what index 0 means.
    'private static View[] outRingViews(ViewGroup container)',
    'private static int[] slotOrder(TrioAppearance a)',
    'private static View slotView(int slot, View signal, View perf, View type)',
    'private static int[] outRingDropOrder(TrioAppearance a)',
    'private static int outGapFor(int slot, ViewGroup container, int labelInward)'
) | ForEach-Object {
    $m=[regex]::Match($hooks,'(?s)'+[regex]::Escape($_)+' \{.*?\n    \}')
    if(!$m.Success){throw "Production method missing: $_"}
    $m.Value
}
foreach($name in @('OutRingLayout.java','Refl.java')){
    Write-Source "io/github/fengwenhua/colorduo/$name" ([IO.File]::ReadAllText((Join-Path $trio $name)))
}
# The order ids and their codec come from the real Prefs, so the harness cannot disagree
# with the app about what a slot number means.
Write-Source 'io/github/fengwenhua/colorduo/Prefs.java' @'
package io.github.fengwenhua.colorduo;
final class Prefs {
 static final int OUT_SLOT_SIGNAL=1, OUT_SLOT_READOUTS=2, OUT_SLOT_TYPE=3;
 static final int[] OUT_PRIORITY={ OUT_SLOT_SIGNAL, OUT_SLOT_READOUTS, OUT_SLOT_TYPE };
 static final int[] OUT_DEFAULT_ORDER={ OUT_SLOT_SIGNAL, OUT_SLOT_READOUTS, OUT_SLOT_TYPE };
 static int outOrderIndex(int[] order,int slot){
  for(int i=0;i<order.length;i++) if(order[i]==slot) return i;
  return order.length;
 }
}
'@
Write-Source 'android/view/View.java' @'
package android.view;
public class View {
 public static final int VISIBLE=0,GONE=8,LAYOUT_DIRECTION_RTL=1;
 public int l,t,r,b,mw,mh,visibility=VISIBLE,dir,pl,pt,pr,pb,paddingWrites;
 public float alpha=1,sx=1,sy=1,px,py,tx;
 public final java.util.Queue<Runnable> posted=new java.util.ArrayDeque<>();
 public boolean post(Runnable runnable){posted.add(runnable);return true;}
 public int getVisibility(){return visibility;}public int getLayoutDirection(){return dir;}
 public int getMeasuredWidth(){return mw;}public int getMeasuredHeight(){return mh;}
 public int getWidth(){return r-l;}public int getHeight(){return b-t;}
 public int getLeft(){return l;}public int getRight(){return r;}public int getTop(){return t;}
 public int getPaddingLeft(){return pl;}public int getPaddingRight(){return pr;}
 public int getPaddingTop(){return pt;}public int getPaddingBottom(){return pb;}
 public void setPadding(int a,int c,int d,int e){pl=a;pt=c;pr=d;pb=e;paddingWrites++;}
 public void layout(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
 public void setAlpha(float v){alpha=v;}public void setScaleX(float v){sx=v;}
 public void setScaleY(float v){sy=v;}public void setPivotX(float v){px=v;}
 public void setPivotY(float v){py=v;}public void setTranslationX(float v){tx=v;}
 public float visualLeft(){return l+tx+px*(1-sx);}
 public float visualRight(){return visualLeft()+getWidth()*sx;}
}
'@
Write-Source 'android/view/ViewGroup.java' @'
package android.view;
public class ViewGroup extends View { public View mStatusIcon; }
'@
Write-Source 'io/github/fengwenhua/colorduo/TrioHooks.java' @"
package io.github.fengwenhua.colorduo;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
final class TrioHooks {
 static final int LOG_INFO=1;
 static final Map<View,int[]> OUT_PAD_SAVED=new WeakHashMap<>();
 static final Map<View,Boolean> OUT_RESERVE_PENDING=new WeakHashMap<>();
 static final Field sStatusIconField=Refl.field(ViewGroup.class,"mStatusIcon");
 static class OutSignalView extends View {}
 static class OutTypeLabel extends View {}
 static class PerfLabel extends View {int stable;String getText(){return "1.4W";}}
 static class Row extends ViewGroup {OutSignalView signal;PerfLabel perf;OutTypeLabel type;View meter=new View();}
 static OutSignalView findOutSignal(ViewGroup row){return ((Row)row).signal;}
 static PerfLabel findPerfLabel(ViewGroup row){return ((Row)row).perf;}
 static OutTypeLabel findOutTypeLabel(ViewGroup row){return ((Row)row).type;}
 static View hostIn(ViewGroup row){return ((Row)row).meter;}
 static View meterIn(View host,ViewGroup row){return ((Row)row).meter;}
 static int[] outLabelMargins(View row){return new int[]{7,5};}
 static int outSignalMargin(View row){return 3;}
 static int perfWidth(PerfLabel perf,String text){return perf.stable;}
 static void log(int level,String text){}static boolean debugLog(){return false;}
 $($methods -join "`n")
 static void reserve(Row row){reserveOutRingStrip(row);}
 static void layout(Row row){layoutOutRingStrip(row,row.meter);}
}
"@
# The arrangement the spliced methods read. Set by each case through Row.appearance, so a
# test can ask for any order without re-splicing the production code.
Write-Source 'io/github/fengwenhua/colorduo/TrioConfig.java' @'
package io.github.fengwenhua.colorduo;
final class TrioConfig {
 static TrioAppearance current;
 static TrioAppearance appearance(){ return current; }
}
'@
Write-Source 'io/github/fengwenhua/colorduo/TrioAppearance.java' @'
package io.github.fengwenhua.colorduo;
final class TrioAppearance {
 final int[] outOrder;
 TrioAppearance(int[] order){ outOrder=order; }
}
'@
Write-Source 'io/github/fengwenhua/colorduo/Os4LayoutTest.java' @'
package io.github.fengwenhua.colorduo;
import android.view.*;
public class Os4LayoutTest {
 static int checks;
 static void ok(String label,boolean b){if(!b)throw new AssertionError(label);checks++;}
 static void near(String label,float a,float b){ok(label+" got="+a+" want="+b,Math.abs(a-b)<0.002f);}
 public static class Monitor {int width;boolean blocked;public int getIslandWidth(){return width;}public boolean getBlocked(){return blocked;}}
 public static class Icons extends View {public Monitor _islandMonitor=new Monitor();}
 static TrioHooks.Row row(){
  TrioConfig.current=new TrioAppearance(Prefs.OUT_DEFAULT_ORDER);
  TrioHooks.Row row=new TrioHooks.Row();row.layout(0,0,230,40);
  Icons icons=new Icons();icons.layout(0,0,200,40);icons.setPadding(2,0,4,0);row.mStatusIcon=icons;
  row.meter.layout(200,0,230,40);
  row.signal=new TrioHooks.OutSignalView();row.signal.mw=20;row.signal.mh=20;
  row.perf=new TrioHooks.PerfLabel();row.perf.mw=30;row.perf.mh=20;row.perf.stable=40;
  row.type=new TrioHooks.OutTypeLabel();row.type.mw=30;row.type.mh=20;
  TrioHooks.reserve(row);TrioHooks.layout(row);return row;
 }
 static View[] views(TrioHooks.Row r){return new View[]{r.signal,r.perf,r.type};}
 static void bounded(TrioHooks.Row r,float floor){
  Icons icons=(Icons)r.mStatusIcon;
  for(View v:views(r))if(v.alpha>0){
   ok("inside row",v.visualLeft()>=-0.002f && v.visualRight()<=icons.getWidth()+0.002f);
   if(r.dir==0)ok("outside island",v.visualLeft()>=floor-0.002f);
   else ok("mirrored outside island",v.visualRight()<=icons.getWidth()-floor+0.002f);
   near("uniform scale",v.sx,v.sy);
  }
  View[] v=views(r);
  for(int i=1;i<v.length;i++){
   if(r.dir==0)ok("no pair overlap",v[i].visualRight()<=v[i-1].visualLeft()+0.002f);
   else ok("no RTL pair overlap",v[i].visualLeft()>=v[i-1].visualRight()-0.002f);
  }
 }
 public static void main(String[] args){
  TrioHooks.Row r=row();Icons icons=(Icons)r.mStatusIcon;
  near("signal",r.signal.visualLeft(),173);near("perf in stable slot",r.perf.visualRight(),168);
  near("type",r.type.visualLeft(),93);near("reserve uses same slots",icons.pr,114);
  float x=r.type.visualLeft();r.perf.mw=20;TrioHooks.layout(r);
  near("live performance digits never move type",r.type.visualLeft(),x);
  icons._islandMonitor.width=60;TrioHooks.layout(r);
  near("island boundary is not a translation",r.type.visualLeft(),x);
  icons._islandMonitor.width=100;TrioHooks.layout(r);bounded(r,100);
  ok("overflow scales whole strip",r.signal.sx<1 && r.signal.sx==r.perf.sx);
  icons._islandMonitor.width=250;TrioHooks.layout(r);
  for(View v:views(r))ok("fully covered strip hides without changing visibility",v.alpha==0 && v.visibility==View.VISIBLE);
  icons._islandMonitor.blocked=true;TrioHooks.layout(r);
  for(View v:views(r))ok("blocked monitor restores strip",v.alpha==1 && v.sx==1);
  icons._islandMonitor.blocked=false;icons._islandMonitor.width=0;TrioHooks.layout(r);
  int writes=icons.paddingWrites;TrioHooks.reserve(r);
  near("repeated reservation does not accumulate",icons.pr,114);ok("stable reserve does not request layout",icons.paddingWrites==writes);
  for(View v:views(r))v.visibility=View.GONE;TrioHooks.reserve(r);
  near("last member removal restores padding",icons.pr,4);
  r=row();icons=(Icons)r.mStatusIcon;icons.layout(0,0,70,40);TrioHooks.layout(r);bounded(r,2);
  ok("former clamp overlap case shrinks",r.signal.sx<1);
  r=row();r.mStatusIcon=null;TrioHooks.layout(r);for(View v:views(r))ok("no row means no unreserved painting",v.alpha==0);
  r=row();icons=(Icons)r.mStatusIcon;
  r.signal.mh=100;TrioHooks.layout(r);bounded(r,2);near("height fits whole strip",r.signal.sy,0.4f);
  ok("layout schedules matching scaled reservation",r.posted.size()==1);
  TrioHooks.layout(r);ok("layout coalesces pending reservation",r.posted.size()==1);
  r.posted.remove().run();near("scaled strip reserves its actual width",icons.pr,48);
  ok("reservation settles without an endless post loop",r.posted.isEmpty());
  ok("100px native icon still fits after scaling",200-icons.pr-100>=icons.pl);
  icons._islandMonitor.width=250;TrioHooks.layout(r);r.posted.remove().run();
  near("fully covered strip returns padding",icons.pr,4);
  icons._islandMonitor.width=0;TrioHooks.layout(r);
  ok("uncovering requests reservation after padding was returned",r.posted.size()==1);
  r.posted.remove().run();near("uncovered strip gets its reservation back",icons.pr,48);
  // Exercise the real adapter, including padding, mirroring, island lookup and
  // fractional view transforms across many constrained frames.
  for(int rtl=0;rtl<2;rtl++)for(int width=20;width<=240;width+=11)for(int boundary=0;boundary<width;boundary+=17){
   r=row();icons=(Icons)r.mStatusIcon;r.dir=rtl;icons.dir=rtl;
   // Restore then switch direction so saved base padding remains unmodified.
   for(View v:views(r))v.visibility=View.GONE;TrioHooks.reserve(r);
   icons.setPadding(rtl==0?2:4,0,rtl==0?4:2,0);
   for(View v:views(r))v.visibility=View.VISIBLE;
   icons.layout(0,0,width,40);icons._islandMonitor.width=boundary;
   TrioHooks.reserve(r);TrioHooks.layout(r);bounded(r,Math.max(2,boundary));
  }
  System.out.println("PASS: OS4 strip / "+checks+" assertions (production layout and reserve methods, simulated Views)");
 }
}
'@
& (Join-Path $jdk 'javac.exe') -nowarn -encoding UTF-8 -d $out $files
if($LASTEXITCODE){throw 'OS4 layout compilation failed'}
& (Join-Path $jdk 'java.exe') -cp $out io.github.fengwenhua.colorduo.Os4LayoutTest
if($LASTEXITCODE){throw 'OS4 layout regression failed'}
