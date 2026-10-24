$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe';$java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}
$trio=Join-Path $root 'app/src/main/java/io/github/fengwenhua/colorduo'
$src=Join-Path $root 'work/capsule-zones/src/io/github/fengwenhua/colorduo'
$classes=Join-Path $root 'work/capsule-zones/classes'
New-Item -ItemType Directory -Force $src,$classes | Out-Null
$probe=@'
package io.github.fengwenhua.colorduo;
import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import io.github.fengwenhua.colorduo.TrioZones.Zone;
import io.github.fengwenhua.colorduo.TrioZones.Part;
public class CapsuleZonesTest {
 static final int SIZE=960;static int checks;
 static void ok(String message,boolean result){if(!result)throw new AssertionError(message);checks++;}
 static TrioSettings config(){TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_CAPSULE;s.roleColors=false;s.trackAlpha=40;s.showWifi=false;s.showBolt=false;return s;}
 static BufferedImage paint(TrioSettings s,int battery,int mobile,int[] slots,String clock){
  TrioState.sClockText=clock;
  BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
  java.awt.Graphics2D g=image.createGraphics();
  TrioRenderer.drawInto(new Canvas(g),SIZE,SIZE,battery,false,false,false,false,-1,mobile,slots,"5GA",0xFF000000,s,true);
  g.dispose();return image;
 }
 static float[] transform(TrioSettings s){return TrioZones.viewTransform(SIZE,SIZE,0,TrioAppearance.of(s));}
 static void differencesBelongTo(BufferedImage a,BufferedImage b,TrioSettings s,Zone expected){
  int changed=0;float[] t=transform(s);TrioAppearance appearance=TrioAppearance.of(s);
  for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
   if(a.getRGB(x,y)!=b.getRGB(x,y)){
    changed++;
    if(TrioZones.zoneAt(t,appearance,x+0.5f,y+0.5f)!=expected)throw new AssertionError(expected+" ink is assigned elsewhere at "+x+","+y);
   }
  }
  ok(expected+" has actual pixels",changed>0);
 }
 static int alpha(BufferedImage image,TrioSettings s,int dot,boolean bottom){
  float x=TrioGeometry.CAP_DOT_CX+(dot-1.5f)*TrioGeometry.CAP_DOT_PITCH;
  float y=bottom?TrioGeometry.capDotBottomY():TrioGeometry.capDotTopY();
  float[] point=new float[2];TrioZones.toView(transform(s),x,y,point);
  return image.getRGB(Math.round(point[0]),Math.round(point[1]))>>>24;
 }
 static Part occupant(TrioSettings s,Zone zone,boolean charging,int wifi){return TrioZones.occupant(zone,TrioAppearance.of(s),79,charging,wifi,"5GA",2);}
 public static void main(String[] args){
  for(int stroke:new int[]{Prefs.MIN_RING_STROKE,Prefs.MAX_RING_STROKE})
   for(int clock:new int[]{Prefs.MIN_CLOCK_SIZE,Prefs.MAX_CLOCK_SIZE})
    for(int value:new int[]{Prefs.MIN_VALUE_SIZE,Prefs.MAX_VALUE_SIZE})
     for(int level:new int[]{5,100}){
      TrioSettings s=config();s.ringStroke=stroke;s.clockSize=clock;s.valueSize=value;
      BufferedImage on=paint(s,level,2,new int[]{2,4},"19:41");
      differencesBelongTo(on,paint(s,level,2,new int[]{2,4},""),s,Zone.TOP);
      TrioSettings off=s.copy();off.showValue=false;
      differencesBelongTo(on,paint(off,level,2,new int[]{2,4},"19:41"),s,Zone.CENTRE);
      float[] t=transform(s);TrioAppearance a=TrioAppearance.of(s);
      for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
       if((on.getRGB(x,y)>>>24)>0 && TrioZones.zoneAt(t,a,x+0.5f,y+0.5f)==null)
        throw new AssertionError("capsule ink has no editable region at "+x+","+y);
      }
      checks++;
     }
  TrioSettings s=config();
  ok("clock occupies the left",occupant(s,Zone.TOP,false,-1)==Part.CLOCK);
  ok("clock has no empty assignment",TrioZones.assign(Zone.TOP,false,null,s,2)==null);
  ok("clock selection preserves all settings",TrioZones.assign(Zone.TOP,false,Part.CLOCK,s,2).changes==0);
  ok("right slot does not accept clock",!TrioZones.choices(Zone.CENTRE,false,s,2).contains(Part.CLOCK));
  for(Zone zone:new Zone[]{Zone.CENTRE,Zone.BOTTOM}){
   TrioZones.Assignment empty=TrioZones.assign(zone,false,null,s,2);
   ok("optional capsule region can be cleared",empty!=null);
   for(boolean charging:new boolean[]{false,true})for(int wifi:new int[]{-1,3}){
    ok("cleared capsule region stays empty",occupant(empty.settings,zone,charging,wifi)==null);
    ok("clearing preserves the fixed clock",occupant(empty.settings,Zone.TOP,charging,wifi)==Part.CLOCK);
   }
  }
  for(Part part:new Part[]{Part.BOLT,Part.WIFI,Part.VALUE,Part.TYPE}){
   TrioZones.Assignment placed=TrioZones.assign(Zone.CENTRE,false,part,s,2);
   ok("right slot accepts "+part,placed!=null);
   ok("right slot renders "+part,occupant(placed.settings,Zone.CENTRE,part==Part.BOLT,3)==part);
   ok("capsule ignores the ring centring switch",placed.settings.valueCentred==s.valueCentred);
  }
  // The ring places the clock in a slot like anything else; the fixed left slot is the
  // capsule's own rule. The rectangle still has no clock.
  {
   TrioSettings rectFrame=s.copy();rectFrame.trioStyle=Prefs.STYLE_RECT;
   for(Zone zone:Zone.values())ok("the rectangle has no clock slot",TrioZones.assign(zone,true,Part.CLOCK,rectFrame,2)==null);
   TrioSettings ringFrame=s.copy();ringFrame.trioStyle=Prefs.STYLE_RING;
   ok("the ring can place the clock",TrioZones.assign(Zone.CENTRE,false,Part.CLOCK,ringFrame,2)!=null);
  }
  BufferedImage weak=paint(s,79,1,null,"19:41"),zero=paint(s,79,0,null,"19:41");
  ok("first signal dot follows mobile level",alpha(weak,s,0,false)>alpha(zero,s,0,false));
  ok("second dot is inactive at mobile level one",alpha(weak,s,1,false)<alpha(weak,s,0,false));
  BufferedImage lowBattery=paint(s,5,1,null,"19:41");
  ok("signal strength is independent of battery",alpha(lowBattery,s,1,false)==alpha(weak,s,1,false));
  s.dualSim=true;BufferedImage dual=paint(s,79,0,new int[]{2,4},"19:41");
  ok("dual rows carry independent SIM readings",alpha(dual,s,2,true)>alpha(dual,s,2,false));
  System.out.println("PASS: "+checks+" capsule editor, pixels, fixed clock and mobile signal checks");
 }
}
'@
$probePath=Join-Path $src 'CapsuleZonesTest.java'
[IO.File]::WriteAllText($probePath,$probe,[Text.UTF8Encoding]::new($false))
$sources=@(Get-ChildItem -Recurse -File -Filter *.java (Join-Path $root 'tests/shims') | ForEach-Object {$_.FullName})
foreach($file in @('Prefs.java','ChargeLayout.java','TrioSettings.java','TrioAppearance.java','TrioGeometry.java','TrioRenderer.java','TrioZones.java')){$sources+=Join-Path $trio $file}
$sources+=$probePath
& $javac -nowarn -encoding UTF-8 -d $classes $sources
if($LASTEXITCODE){throw 'Capsule editor compilation failed'}
& $java -Xmx512m -cp $classes io.github.fengwenhua.colorduo.CapsuleZonesTest
if($LASTEXITCODE){throw 'Capsule editor verification failed'}
