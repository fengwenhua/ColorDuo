/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;
import android.content.Context;
import android.view.*;
import java.util.Arrays;
public class IssueRegressionTest {
 static int checks;
 @org.junit.Before public void reset() { resetFixture(); }
 @org.junit.After public void report() { System.out.println("Issue assertions: "+checks); }
 static void resetFixture() {
  checks=0; android.os.SystemClock.now=10000;
  TrioGeometry.setCapsuleLength(0);
  TrioConfig.settings=TrioSettings.defaults();
  TrioState.sWifiLevel=TrioState.sMobileLevel=-1; TrioState.sDataSlot=0;
  TrioState.sWifiPresent=TrioState.sIslandHideBattery=false;
  TrioState.sMobileType=""; TrioState.sSlotLevels=new int[]{-1,-1};
  TrioState.sSimsDueAt=0; TrioState.nextLevel=2; TrioState.polls=0; TrioState.sContext=null;
  TrioHooks.ROW_INK.clear(); TrioHooks.ROW_TINTS.clear(); TrioHooks.hooks.clear();
  TrioHooks.tintHook=null; TrioHooks.sStatusClock=null; TrioHooks.sClockSetter=null;
  TrioHooks.sClockHidden=TrioHooks.covered=TrioHooks.island=false; TrioHooks.bar=null;
 }
 @org.junit.Test public void signalRegression() throws Throwable { signal(); }
 @org.junit.Test public void tintRegression() throws Throwable { tint(); }
 @org.junit.Test public void assignmentRegression() throws Throwable { assignment(); }
 @org.junit.Test public void wifiRegression() throws Throwable { wifi(); }
 @org.junit.Test public void clearRegression() throws Throwable { clear(); }
 @org.junit.Test public void clockRegression() throws Throwable { clock(); }
 @org.junit.Test public void centreRegression() throws Throwable { centre(); }
 @org.junit.Test public void dotsRegression() throws Throwable { dots(); }
 @org.junit.Test public void outorderRegression() throws Throwable { outorder(); }
 @org.junit.Test public void capsuleRegression() throws Throwable { capsule(); }
 @org.junit.Test public void isolationRegression() throws Throwable { isolation(); }
 @org.junit.Test public void nudgesRegression() throws Throwable { nudges(); }
 static void ok(String message,boolean value){if(!value)throw new AssertionError(message);checks++;}
 static View host(Context c){ViewGroup row=new ViewGroup(c);View v=new View(c);v.parent=row;row.host=v;return v;}
 static void signal() {
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.dualSim=false;
  TrioState.sMobileLevel=-1;TrioState.sSlotLevels=new int[]{-1,-1};TrioState.sSimsDueAt=0;TrioState.polls=0;
  TrioState s=new TrioState(host(new Context()));TrioState.nextLevel=2;s.refresh();
  ok("initial telephony fallback",s.mobileLevel==2 && TrioState.polls==1);
  TrioState.nextLevel=4;android.os.SystemClock.now+=1999;s.refresh();
  ok("fallback retains the two-second rate limit",s.mobileLevel==2 && TrioState.polls==1);
  android.os.SystemClock.now++;s.refresh();
  ok("fallback refreshes after first successful reading",s.mobileLevel==4 && TrioState.polls==2);
  TrioState.sMobileLevel=3;android.os.SystemClock.now+=2000;s.refresh();
  ok("known icon source skips unnecessary telephony polling",s.mobileLevel==3 && TrioState.polls==2);
 }
 static void tint() throws Throwable {
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
  View a=host(new Context()),b=host(new Context());ViewGroup ar=(ViewGroup)a.parent,br=(ViewGroup)b.parent;
  TrioHooks.ROW_INK.put(ar,0xFFFFFFFF);TrioHooks.ROW_INK.put(br,0xFFFFFFFF);
  for(float intensity:new float[]{0f,0.5f,1f}) {
   TrioHooks.tint(a,intensity,false,0);
   int want=intensity>0?0xFF000000:0xFFFFFFFF;
   ok("meter keeps fractional intensity "+intensity,ar.ink==want);
   ok("meter archives latest ink "+intensity,TrioHooks.rowInk(ar)==want);
   ok("draw and meter agree "+intensity,new TrioState(a).foreground()==want);
   ok("other row remains independent",TrioHooks.rowInk(br)==0xFFFFFFFF);
  }
  TrioConfig.settings.mobileTypeMode=Prefs.MOBILE_TYPE_OFF;
  TrioHooks.tint(a,0f,true,0xFF123456);TrioHooks.tint(b,0f,true,0xFF654321);
  ok("type disabled still receives tint",TrioHooks.rowInk(ar)==0xFF123456 && ar.ink==0xFF123456);
  ok("interleaved row tint remains local",TrioHooks.rowInk(br)==0xFF654321 && TrioHooks.rowInk(ar)==0xFF123456);
  int before=a.invalidations;TrioHooks.tint(a,0f,true,0xFF123456);
  ok("unchanged tint avoids host redraw",a.invalidations==before);
  java.util.ArrayList<android.graphics.Rect> areas=new java.util.ArrayList<>();
  android.graphics.Rect area=new android.graphics.Rect(0,0,500,100);areas.add(area);
  for(boolean use:new boolean[]{false,true}) {
   TrioHooks.tint(a,areas,0.5f,use,0xFF123456);
   ok("outside dark area stays light "+use,TrioHooks.rowInk(ar)==0xFFFFFFFF && ar.ink==0xFFFFFFFF);
  }
  area.left=850;area.right=1000;
  TrioHooks.tint(a,areas,0.5f,true,0xFF123456);
  ok("inside tint area uses tint",TrioHooks.rowInk(ar)==0xFF123456);
  area.left=0;area.right=500;areas.clear();
  ok("cached area owns a copy of list and rect",TrioHooks.rowInk(ar)==0xFF123456);
  a.x=0;
  ok("host movement re-evaluates cached area",TrioHooks.rowInk(ar)==0xFFFFFFFF);
  TrioHooks.iconTint(a,new java.util.ArrayList<>(),0xFF000000);
  ok("neighbour tint cannot overwrite meter area",TrioHooks.rowInk(ar)==0xFFFFFFFF);
  View c=host(new Context());ViewGroup cr=(ViewGroup)c.parent;
  areas.add(new android.graphics.Rect(0,0,500,100));
  TrioHooks.iconTint(c,areas,0xFF000000);
  ok("icon-only fallback respects battery area",TrioHooks.rowInk(cr)==0xFFFFFFFF);
  TrioHooks.tint(c,0.5f,true,0xFF654321);
  TrioHooks.iconTint(c,areas,0xFF000000);
  ok("meter dispatch wins in both callback orders",TrioHooks.rowInk(cr)==0xFF654321);
  areas.clear();areas.add(new android.graphics.Rect(900,0,930,100));
  TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("exactly half coverage stays light like native",TrioHooks.rowInk(cr)==0xFFFFFFFF);
  areas.get(0).right=931;TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("more than half coverage uses tint",TrioHooks.rowInk(cr)==0xFF000000);
  areas.get(0).top=1;TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("area below the bar does not tint",TrioHooks.rowInk(cr)==0xFFFFFFFF);
 }
 static void wifi() {
  // A fresh install starts blank, so this places the arcs explicitly - the point of the
  // case is the READING, not the default layout.
  TrioSettings s=TrioSettings.defaults();s.showValue=false;s.mobileTypeMode=Prefs.MOBILE_TYPE_OFF;s.dualSim=false;
  s.ringZones=Prefs.decodeLayout("|1|");
  ok("no Wi-Fi reading leaves the middle empty",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,-1,"5G",2)==null);
  ok("a live Wi-Fi reading still names the arcs",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,3,"5G",2)==TrioZones.Part.WIFI);
  s.showWifi=false;
  ok("the Wi-Fi switch still controls live arcs",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,3,"5G",2)==null);
 }
 static void clear() {
  for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_RECT}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;s.showValue=false;s.valueCentred=true;
   s.dualSim=false;s.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
   for(TrioZones.Zone zone:TrioZones.Zone.values()) {
   // LEFT is the capsule's fourth cell; the ring and the rectangle have three.
   // Asserting a clear on a cell an arrangement does not have would be asserting
   // a shape it was never given, so the suite skips it there.
   if(zone==TrioZones.Zone.LEFT && style!=Prefs.STYLE_CAPSULE) { continue; }
    TrioZones.Assignment a=TrioZones.assign(zone,style==Prefs.STYLE_RECT,null,s,2);
    ok("zone can be cleared "+style+"/"+zone,a!=null);
    for(boolean charging:new boolean[]{false,true})for(int wifi:new int[]{-1,3}) {
     ok("cleared zone stays empty "+style+"/"+zone+"/"+charging+"/"+wifi,
      TrioZones.occupant(zone,TrioAppearance.of(a.settings),79,charging,wifi,"5G",2)==null);
    }
   }
  }
  // Independent exhaustive check for clearing the middle when both the battery
  // number and the no-Wi-Fi type compete with the arcs. The implementation searches
  // a restricted candidate set; compare its answer with every legal switch edit.
  TrioSettings cur=TrioSettings.defaults();cur.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
  TrioZones.Assignment solved=TrioZones.assign(TrioZones.Zone.CENTRE,false,null,cur,2);
  int minimum=Integer.MAX_VALUE;
  for(int bits=0;bits<64;bits++)for(int mode:new int[]{Prefs.MOBILE_TYPE_OFF,Prefs.MOBILE_TYPE_IN_RING}) {
   TrioSettings c=cur.copy();c.showWifi=(bits&1)!=0;c.showValue=(bits&2)!=0;c.valueCentred=(bits&4)!=0;
   c.showBolt=(bits&8)!=0;c.dualSim=(bits&16)!=0;c.showMobile=(bits&32)!=0;c.mobileTypeMode=mode;
   boolean empty=true;
   for(boolean charging:new boolean[]{false,true})for(int wifi:new int[]{-1,3}) {
    empty &= TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(c),79,charging,wifi,"5G",2)==null;
   }
   if(empty) {
    int changes=(c.showWifi!=cur.showWifi?1:0)+(c.showValue!=cur.showValue?1:0)
     +(c.valueCentred!=cur.valueCentred?1:0)+(c.showBolt!=cur.showBolt?1:0)
     +(c.dualSim!=cur.dualSim?1:0)+(c.showMobile!=cur.showMobile?1:0)+(c.mobileTypeMode!=cur.mobileTypeMode?1:0);
    minimum=Math.min(minimum,changes);
   }
  }
  ok("clearing all four frames uses the smallest legal edit",solved!=null && solved.changes==minimum);
 }
 static void assignment() {
  // Ring: placement is a priority list, so "clear" empties that zone's list and leaves the
  // switch alone - the placement lives in the layout, not in the switch.
  {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   s.mobileTypeMode=Prefs.MOBILE_TYPE_OUT_RING;
   TrioSettings placed=TrioZones.withPiece(s,TrioZones.Zone.BOTTOM.ordinal(),TrioZones.Part.MOBILE);
   ok("placing mobile leaves outside type unchanged",placed.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
   ok("the placed piece heads the zone's list",
      placed.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_MOBILE);
   TrioSettings cleared=TrioZones.withPiece(placed,TrioZones.Zone.BOTTOM.ordinal(),null);
   ok("clearing empties the zone, not the switch",
      cleared.ringZones[TrioZones.Zone.BOTTOM.ordinal()].length==0 && cleared.showMobile);
   // Dropping one entry is a different operation and reveals the candidate behind it.
   TrioSettings two=TrioZones.withPiece(placed,TrioZones.Zone.BOTTOM.ordinal(),TrioZones.Part.WIFI);
   ok("a second piece goes to the front",
      two.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_WIFI);
   TrioSettings one=TrioZones.withoutAt(two,TrioZones.Zone.BOTTOM.ordinal(),0);
   ok("dropping the head reveals the next candidate",
      one.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_MOBILE);
  }
  // Rectangle: still one switch per zone, and the solver still answers.
  {
   int style=Prefs.STYLE_RECT;
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;s.mobileTypeMode=Prefs.MOBILE_TYPE_OUT_RING;
   TrioZones.Assignment a=TrioZones.assign(TrioZones.Zone.BOTTOM,true,TrioZones.Part.MOBILE,s,2);
   ok("rect: selecting existing mobile leaves outside type unchanged",a!=null && a.changes==0 && a.settings.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
   a=TrioZones.assign(TrioZones.Zone.BOTTOM,true,null,s,2);
   ok("rect: clearing mobile switches it off",a!=null && a.changes==1 && !a.settings.showMobile);
  }
  TrioSettings s=TrioSettings.defaults();s.dualSim=false;
  // Two cards are two placed pieces now, not a second row of the aggregate meter, so
  // what has to be reachable is each card's own component.
  ok("either card's row can be placed",TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_1,s,2)!=null
     && TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_2,s,2)!=null);
 }
 public static class NativeClock extends View {
  int calls,value;boolean fail=true;
  NativeClock(){super(new Context());}
  public void setMultiTaskVisibility(int visibility){calls++;if(fail){fail=false;throw new IllegalStateException("transient clock setter failure");}value=visibility;}
 }
 static void clock()throws Exception {
  NativeClock clock=new NativeClock();TrioHooks.sStatusClock=clock;
  TrioHooks.sClockSetter=NativeClock.class.getDeclaredMethod("setMultiTaskVisibility",int.class);
  TrioHooks.sClockSetter.setAccessible(true);TrioHooks.sClockHidden=false;
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.trioStyle=Prefs.STYLE_CAPSULE;
  TrioHooks.applyClockVisibility();
  ok("failed clock hide is not cached as success",!TrioHooks.sClockHidden && clock.calls==1);
  TrioHooks.applyClockVisibility();
  ok("failed clock hide retries",TrioHooks.sClockHidden && clock.calls==2 && clock.value==8);
  TrioHooks.applyClockVisibility();ok("stable clock hiding is idempotent",clock.calls==2);
  TrioConfig.settings.trioStyle=Prefs.STYLE_RING;TrioHooks.applyClockVisibility();
  ok("leaving capsule restores native clock",!TrioHooks.sClockHidden && clock.value==0);
  TrioConfig.settings.trioStyle=Prefs.STYLE_CAPSULE;TrioHooks.applyClockVisibility();
  TrioConfig.settings.enabled=false;TrioHooks.applyClockVisibility();
  ok("master off restores native clock",!TrioHooks.sClockHidden && clock.value==0);
   // The left column is the user's now (issues 44 and 47). A capsule that puts
   // something else there must leave the bar its clock: hiding the clock is only
   // correct while the capsule is the one drawing the time.
   TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.trioStyle=Prefs.STYLE_CAPSULE;
   TrioConfig.settings.enabled=true;
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_CLOCK}};
   ok("a capsule whose left cell holds the clock hides the bar clock",
      TrioConfig.appearance().hidesStatusClock());
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_WIFI}};
   ok("a left cell of Wi-Fi leaves the bar clock alone",
      !TrioConfig.appearance().hidesStatusClock());
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_VALUE}};
   ok("so does the percentage",!TrioConfig.appearance().hidesStatusClock());
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{}};
   ok("an empty left list is the shipped look and keeps the clock",
      TrioConfig.appearance().hidesStatusClock());
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_CLOCK,Prefs.PART_VALUE}};
   ok("the left cell draws its first entry",
      TrioConfig.appearance().capsule(73,false,3,"5GA",1)
              .parts[TrioZones.Zone.LEFT.ordinal()]==Prefs.PART_CLOCK);
   ok("and never the retired stand-alone setting",
      TrioConfig.appearance().capsule(73,false,3,"5GA",1)
              .parts[TrioZones.Zone.LEFT.ordinal()]!=Prefs.PART_WIFI);
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_VALUE}};
   ok("a laid-out left cell is not overridden by the shipped look",
      TrioConfig.appearance().capsule(73,false,3,"5GA",1)
              .parts[TrioZones.Zone.LEFT.ordinal()]==Prefs.PART_VALUE);
   TrioConfig.settings.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
   TrioConfig.settings.ringZones=new int[][]{{},{},{},{Prefs.PART_TYPE,Prefs.PART_VALUE}};
   ok("and the order decides between two live candidates",
      TrioConfig.appearance().capsule(73,false,3,"5GA",1)
              .parts[TrioZones.Zone.LEFT.ordinal()]==Prefs.PART_TYPE);


   // issue 45: the temperature can be tied to the charger. A separate key, so an
   // install that already shows the temperature keeps it after the upgrade - which
   // is why the default has to stay off.
   TrioSettings tempOff=TrioSettings.defaults();
   ok("temperature is not tied to the charger by default",
      !tempOff.perfTempChargingOnly && !Prefs.DEF_PERF_TEMP_CHARGING_ONLY);
   tempOff.perfTemp=true;tempOff.perfTempChargingOnly=true;
   ok("the flag survives appearance resolution",
      TrioAppearance.of(tempOff).perfTempChargingOnly);
   ok("and can be carried alone",
      !TrioAppearance.of(tempOff.copy()).perfTempChargingOnly==false);
   TrioSettings tempCopy=tempOff.copy();
   ok("copy() carries the flag",tempCopy.perfTempChargingOnly);
   ok("and the bundle round-trips it",
      TrioSettings.fromBundle(tempOff.toBundle()).perfTempChargingOnly);
 }
 /**
  * A signal row may not be placed in the ring's middle band, and every path that could
  * place one refuses it there.
  *
  * <p>The rule exists because the middle band has no opening for the row to sit in: the
  * arc runs past both sides, so four beads there are a horizontal smudge across a closed
  * ring rather than the ring cut into segments. The test is stated against the three
  * doors a placement can come through - the layout write, the solver and the picker's own
  * list - because a rule enforced at one of them is a rule the other two can walk around.
  */
 static void centre() {
  for(TrioZones.Part row:new TrioZones.Part[]{
      TrioZones.Part.MOBILE,TrioZones.Part.SIM_1,TrioZones.Part.SIM_2}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   ok("the ring refuses "+row+" in the middle",!TrioZones.ringAccepts(TrioZones.Zone.CENTRE,row));
   ok("and accepts it at the top",TrioZones.ringAccepts(TrioZones.Zone.TOP,row));
   ok("and at the bottom",TrioZones.ringAccepts(TrioZones.Zone.BOTTOM,row));
   // Door 1: the layout write hands the settings back untouched rather than storing a
   // placement the drawing would skip.
   TrioSettings refused=TrioZones.withPiece(s,TrioZones.Zone.CENTRE.ordinal(),row);
   ok("withPiece refuses "+row+" in the middle",
      refused.ringZones[TrioZones.Zone.CENTRE.ordinal()].length==0);
   ok("withPiece does not mutate on refusal",s.ringZones[TrioZones.Zone.CENTRE.ordinal()].length==0);
   ok("withPiece still places "+row+" at the bottom",
      TrioZones.withPiece(s,TrioZones.Zone.BOTTOM.ordinal(),row)
        .ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==TrioZones.encodePart(row));
   // Door 2: the solver reports it as unsolvable, so the page's row is disabled.
   ok("the solver refuses "+row+" in the middle",
      TrioZones.assign(TrioZones.Zone.CENTRE,false,row,s,2)==null);
   ok("the solver places "+row+" at the bottom",
      TrioZones.assign(TrioZones.Zone.BOTTOM,false,row,s,2)!=null);
   // Door 3: the picker never offers it, so there is no dead menu entry. MOBILE is the
   // retired aggregate meter and is absent from every list by construction - its own
   // absence is asserted elsewhere - so only the two live rows are checked here.
   ok("the picker does not offer "+row+" in the middle",
      !TrioZones.placementChoices(TrioZones.Zone.CENTRE,s,2).contains(row));
   if(row!=TrioZones.Part.MOBILE) {
    ok("the picker offers "+row+" at the bottom",
       TrioZones.placementChoices(TrioZones.Zone.BOTTOM,s,2).contains(row));
   }
   // And the drawing passes over a layout that still names it, so a snapshot delivered
   // And the drawing passes over a layout that still names it. Decoding repairs such a
   // layout today, so this asserts the drawing's own guard by handing it the raw arrays a
   // broadcast could carry - a snapshot built by a process that never decoded a string.
   // Two shapes, because the guard must do both: skip the refused head, and let the
   // candidate behind it take the band rather than leaving it blank.
   int code=TrioZones.encodePart(row);
   for(int[] chain:new int[][]{{code},{code,Prefs.PART_WIFI}}) {
    TrioSettings stale=s.copy();
    stale.showSim1=stale.showSim2=true;stale.showMobile=true;stale.showWifi=true;
    // Index 1 is the middle band; 0 and 2 are the openings.
    stale.ringZones=new int[][]{{},chain,{}};
    int slot=TrioAppearance.of(stale).ring(79,false,3,"5G",2)
      .parts[TrioZones.Zone.CENTRE.ordinal()];
    ok("the drawing skips a stored middle "+row+" in "+Arrays.toString(chain),
       slot!=code);
    ok("and takes the fallback behind it, not a blank band "
       +Arrays.toString(chain),
       chain.length==1 ? slot==Prefs.PART_NONE : slot==Prefs.PART_WIFI);
   }
  }
  // Everything else keeps the middle, so the rule is the signal's and not a blanket ban.
  for(TrioZones.Part part:TrioZones.Part.values()) {
   if(TrioZones.isSignalRow(part)) continue;
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   ok("the middle still takes "+part,TrioZones.ringAccepts(TrioZones.Zone.CENTRE,part));
   ok("and the solver places "+part+" there",
      TrioZones.assign(TrioZones.Zone.CENTRE,false,part,s,2)!=null);
  }
  // Decoding a layout written before the rule moves the row to the bottom band instead of
  // deleting it: dropping it would read as "my setting was lost". The middle holds a
  // reading ahead of the row here, so the rest of that band has to survive untouched.
  int[][] decoded=Prefs.decodeLayout("|3,7|");
  ok("a stored middle signal row leaves the middle",
     !Arrays.equals(decoded[Prefs.ZONE_CENTRE],new int[]{Prefs.PART_VALUE,Prefs.PART_SIM_1}));
  ok("and arrives in the bottom band",
     Arrays.equals(decoded[Prefs.ZONE_BOTTOM],new int[]{Prefs.PART_SIM_1}));
  ok("the rest of the middle survives, in order",
     Arrays.equals(decoded[Prefs.ZONE_CENTRE],new int[]{Prefs.PART_VALUE}));
  // Appended behind whatever the bottom band already held, so a deliberately promoted
  // piece keeps its precedence.
  ok("a moved row goes behind the bottom band's own head",
     Arrays.equals(Prefs.decodeLayout("|7|1")[Prefs.ZONE_BOTTOM],
        new int[]{Prefs.PART_WIFI,Prefs.PART_SIM_1}));
  // A row already in the bottom band is not duplicated by the move.
  ok("a row already at the bottom is not duplicated",
     Arrays.equals(Prefs.decodeLayout("|7|7")[Prefs.ZONE_BOTTOM],
        new int[]{Prefs.PART_SIM_1}));
  // Repair is idempotent: running it twice must not move anything twice.
  int[][] once=TrioZones.repairRingLayout(Prefs.decodeLayout("|7|1"));
  ok("repairing a repaired layout changes nothing",
     Arrays.deepEquals(once,TrioZones.repairRingLayout(once)));
  // The other two arrangements have no such band, so their middle keeps everything.
  for(int style:new int[]{Prefs.STYLE_RECT,Prefs.STYLE_CAPSULE}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;
   for(TrioZones.Part row:new TrioZones.Part[]{
       TrioZones.Part.MOBILE,TrioZones.Part.SIM_1,TrioZones.Part.SIM_2}) {
    ok("style "+style+" keeps "+row+" in its middle",
       TrioZones.accepts(TrioZones.Zone.CENTRE,row,s));
   }
  }
 }
 /**
  * The ring's level-dot size setting, which has to scale the bead without letting it
  * outgrow the ink box.
  *
  * <p>Two claims, and both are needed. The first is that the slider does something the
  * drawing can see: the bead radius has to follow it. The second is the ceiling - the
  * beads are threaded onto the ring's own arc, so a larger bead reaches further from the
  * ring centre, and the ink box is what fits the glyph to its view. A setting that pushed
  * ink past that box would be clipped by the host and, worse, would make the glyph grow
  * and shrink as the slider moved.
  *
  * <p>The default is asserted to be a no-op, because that is the whole compatibility
  * promise: an install that never touches the key must keep the bead whose diameter is the
  * ring's thickness.
  */
 static void dots() {
  // 1. 100% is exactly the old radius, at every stroke, for both end thirds.
  for(int stroke=Prefs.MIN_RING_STROKE;stroke<=Prefs.MAX_RING_STROKE;stroke++) {
   float width=78f-stroke;
   float legacy=TrioGeometry.arcRowDotR(0,stroke,width);
   float at100=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   ok("100% is the old bead at stroke "+stroke,Math.abs(legacy-at100)<1e-6f);
   // And the two openings share the size: they are one piece of ink.
   ok("the top and bottom rows share one size at stroke "+stroke,
      Math.abs(TrioGeometry.arcRowDotR(0,stroke,width,1f)
             -TrioGeometry.arcRowDotR(2,stroke,width,1f))<1e-6f);
  }
  // 2. Smaller really is smaller, and larger is larger - until the box stops it.
  {
   int stroke=Prefs.DEF_RING_STROKE;
   float width=78f-stroke;
   float small=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MIN_DOT_SCALE/100f);
   float normal=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   float big=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MAX_DOT_SCALE/100f);
   ok("the smallest setting draws a smaller bead",small<normal);
   ok("the largest setting draws a larger bead",big>normal);
   ok("the bead never shrinks below a drawable radius",small>=0.5f);
  }
  // 3. The ceiling: no setting, at any stroke, may ask for a bead the box cannot hold,
  // and none may ask for one the opening cannot hold either. Both limits are real - the
  // box stops the row leaving the glyph, the opening stops it overrunning the arc's caps -
  // and whichever binds first is the one the drawing must obey.
  for(int stroke=Prefs.MIN_RING_STROKE;stroke<=Prefs.MAX_RING_STROKE;stroke++) {
   float width=78f-stroke;
   for(int pct:new int[]{Prefs.MIN_DOT_SCALE,Prefs.DEF_DOT_SCALE,Prefs.MAX_DOT_SCALE}) {
    float r=TrioGeometry.arcRowDotR(0,stroke,width,pct/100f);
    ok("stroke "+stroke+" at "+pct+"% stays inside the ink box",
       r<=TrioGeometry.maxBeadR()+0.01f);
    // The reference bead is what the opening was designed around; the test asserts the
    // drawn radius never exceeds the roomier of the two mechanical limits, which is what
    // keeps a future change to either from silently overlapping the arc.
    float reference=TrioGeometry.arcRowDotR(0,stroke,width);
    float room=Math.max(reference,TrioGeometry.maxBeadR());
    ok("stroke "+stroke+" at "+pct+"% is capped by a real limit, not the slider",
       r<=Math.max(reference,room)+0.01f);
   }
   // Scaling up can never make the bead smaller than the reference: the cap only binds
   // from above, so a large setting is monotonic up to the ceiling.
   float normal=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   float big=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MAX_DOT_SCALE/100f);
   ok("a larger setting never draws a smaller bead at stroke "+stroke,big>=normal-1e-6f);
  }
  // 4. The setting round-trips, and a stored value outside the span is clamped rather
  // than trusted - the hooked side reads the broadcast extras as well as the file.
  {
   TrioSettings s=TrioSettings.defaults();
   ok("a fresh install is at the reference size",s.dotScale==Prefs.DEF_DOT_SCALE);
   TrioSettings tweaked=s.copy();tweaked.dotScale=Prefs.MAX_DOT_SCALE;
   ok("the dot size survives copy()",tweaked.copy().dotScale==Prefs.MAX_DOT_SCALE);
   ok("the dot size survives the bundle",TrioSettings.fromBundle(
        tweaked.toBundle()).dotScale==Prefs.MAX_DOT_SCALE);
   ok("the dot size participates in equality",!s.equals(tweaked));
   ok("the remote key update carries it",s.applyKeyFrom(tweaked,Prefs.KEY_DOT_SCALE)
        && s.dotScale==Prefs.MAX_DOT_SCALE);
   TrioSettings wild=s.copy();wild.dotScale=9999;
   ok("the multiplier clamps a wild value",
      TrioAppearance.of(wild).dotScaleFactor()==Prefs.MAX_DOT_SCALE/100f);
  }
 }
 /**
  * The out-of-ring order: a stored arrangement that decides placement only.
  *
  * <p>Three claims. The codec survives junk and always yields three slots, because it is
  * read from a preference file and from broadcast extras. The arrangement actually moves the
  * views through {@link OutRingLayout}, since an order that reaches the settings but not the
  * layout would look saved and do nothing. And the sacrifice order stays a separate
  * judgement: reordering must not change which piece survives an overflow, which is the whole
  * reason the two were split.
  */
 static void outorder() {
  // 1. The codec: order preserved, junk repaired, always three entries.
  ok("the default order is the shipped arrangement",
     Arrays.equals(Prefs.decodeOutOrder(null),new int[]{
        Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_TYPE}));
  ok("a stored order round-trips",
     Arrays.equals(Prefs.decodeOutOrder("3,1,2"),new int[]{
        Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS}));
  ok("the codec re-encodes to the same string",
     "3,1,2".equals(Prefs.encodeOutOrder(Prefs.decodeOutOrder("3,1,2"))));
  // A partial value keeps what the user did express and appends the rest in default order,
  // rather than resetting the whole arrangement over one damaged id.
  ok("a partial order keeps the entry that survived",
     Arrays.equals(Prefs.decodeOutOrder("3"),new int[]{
        Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS}));
  ok("an unknown id is dropped",Prefs.decodeOutOrder("9,1,2,3").length==Prefs.OUT_SLOT_COUNT);
  ok("a duplicate is collapsed",
     Arrays.equals(Prefs.decodeOutOrder("1,1,2,3"),new int[]{
        Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_TYPE}));
  ok("an empty string is the default order",
     Arrays.equals(Prefs.decodeOutOrder(""),Prefs.OUT_DEFAULT_ORDER));
  ok("every decoded order names each slot exactly once",distinctSlots(Prefs.decodeOutOrder("x")));
  // 2. The transport: the arrangement survives copy, bundle and the remote key update.
  {
   TrioSettings s=TrioSettings.defaults();
   ok("a fresh install has the shipped arrangement",
      Arrays.equals(s.outOrder,Prefs.OUT_DEFAULT_ORDER));
   s.outOrder=new int[]{Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_SIGNAL};
   ok("the order survives copy()",Arrays.equals(s.copy().outOrder,s.outOrder));
   ok("copy() does not share the array",s.copy().outOrder!=s.outOrder);
   ok("the order survives the bundle",
      Arrays.equals(TrioSettings.fromBundle(s.toBundle()).outOrder,s.outOrder));
   TrioSettings other=TrioSettings.defaults();
   ok("the remote key update carries it",other.applyKeyFrom(s,Prefs.KEY_OUT_ORDER)
      && Arrays.equals(other.outOrder,s.outOrder));
   ok("the order participates in equality",!TrioSettings.defaults().equals(s));
   ok("the appearance exposes the same arrangement",
      Arrays.equals(TrioAppearance.of(s).outOrder,s.outOrder));
  }
  // 3. The layout: the arrangement decides placement, and only placement.
  //
  // The coordinate convention, which the editor has to mirror: `near` is measured from the
  // row's END, so the slot the solver counts FIRST sits closest to the battery and therefore
  // furthest to the RIGHT in LTR. Index 0 is "next to the battery", not "leftmost" - that is
  // the mapping the settings row has to reverse when it draws its tiles.
  {
   int[] widths={20,40,30}, heights={20,20,20}, gaps={3,5,5};
   OutRingLayout.Plan shipped=OutRingLayout.solve(300,0,0,0,40,widths,heights,gaps,7);
   ok("index 0 is placed at all",shipped.widths[0]>0);
   ok("index 0 sits nearest the row's end, i.e. nearest the battery",
      shipped.near[0]>shipped.near[1] && shipped.near[1]>shipped.near[2]);
   // Reversed arrangement: the same widths, permuted. The same index now carries a different
   // piece, so its coordinate moves - that is what a reorder has to look like.
   int[] revWidths={widths[2],widths[1],widths[0]};
   int[] revGaps={gaps[2],gaps[1],gaps[0]};
   OutRingLayout.Plan reversed=OutRingLayout.solve(300,0,0,0,40,revWidths,heights,revGaps,7);
   ok("a permuted arrangement places the same pieces",
      Arrays.equals(sorted(shipped.widths),sorted(reversed.widths)));
   ok("the arrangement moves the geometry",
      Math.abs(shipped.near[2]-reversed.near[2])>0.5f);
   // And the screen order the editor renders is the reverse of this array: the LAST stored
   // entry is the leftmost tile. Stated as arithmetic so the two cannot drift apart.
   int[] stored={Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_READOUTS};
   int[] onScreen={stored[2],stored[1],stored[0]};
   ok("the leftmost tile is the piece furthest from the battery",
      onScreen[0]==Prefs.OUT_SLOT_READOUTS && stored[2]==Prefs.OUT_SLOT_READOUTS);
   ok("the rightmost tile is the piece next to the battery",
      onScreen[onScreen.length-1]==stored[0]);
  }
  // 4. Priority is independent: overflow drops the same piece whatever the arrangement.
  {
   int[] widths={40,40,40}, heights={20,20,20}, gaps={5,5,5};
   // No drop order: the old positional rule, which sacrifices from the end.
   OutRingLayout.Plan positional=OutRingLayout.solve(30,0,0,0,40,widths,heights,gaps,0);
   ok("positional rule drops from the end",positional.dropped[2]);
   // An explicit priority that names slot 0 first must sacrifice slot 0 instead, whatever
   // position it occupies - that is the separation the setting exists for.
   OutRingLayout.Plan priority=OutRingLayout.solve(30,0,0,0,40,widths,heights,gaps,0,
      new int[]{0,1,2});
   ok("an explicit priority wins over the position",priority.dropped[0]);
   ok("and it drops exactly one before giving up",
      priority.dropped[0] && !priority.dropped[1] && !priority.dropped[2]);
   // A partial order still terminates: everything it names is gone, so the fallback takes
   // over rather than looping forever.
   OutRingLayout.Plan partial=OutRingLayout.solve(1,0,0,0,40,widths,heights,gaps,0,
      new int[]{0});
   ok("a partial drop order still terminates",partial.dropped[0]);
   ok("the default order and the priority agree on the shipped arrangement",
      Arrays.equals(Prefs.OUT_DEFAULT_ORDER,Prefs.OUT_PRIORITY));
  }
 }
 /** True when an order names each slot exactly once. */
 static boolean distinctSlots(int[] order) {
  if(order.length!=Prefs.OUT_SLOT_COUNT) return false;
  boolean[] seen=new boolean[Prefs.OUT_SLOT_COUNT+1];
  for(int id:order) {
   if(id<Prefs.OUT_SLOT_SIGNAL||id>Prefs.OUT_SLOT_COUNT||seen[id]) return false;
   seen[id]=true;
  }
  return true;
 }
 /** A sorted copy, so two arrangements can be compared as multisets. */
 static int[] sorted(int[] values) {
  int[] copy=values.clone();
  Arrays.sort(copy);
  return copy;
 }
 /**
  * The capsule's two dot rows are editable placements, and an install that never edited one
  * still draws exactly what it used to.
  *
  * <p>The defect this pins: the openings drew dots from the mobile switch alone, so the user
  * saw signal dots in the top and bottom bands and no setting could move or remove them - the
  * bands opened a picker that offered readings only, and the rows were not in any list. The
  * fix makes them band occupants like everything else; the compatibility claim is that the
  * legacy no-placement path reproduces the old look, which is why the two rows are asserted
  * on instead of merely "dots are still drawn somewhere".
  */
 static void capsule() {
  // 1. A capsule with nothing placed keeps the shipped look: both rows, from the switch.
  {
   TrioSettings legacy=TrioSettings.defaults();
   legacy.trioStyle=Prefs.STYLE_CAPSULE;
   legacy.ringZones=Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
   TrioAppearance a=TrioAppearance.of(legacy);
   TrioAppearance.Capsule cap=a.capsule(79,false,-1,"5GA",2);
   ok("an untouched capsule still draws dots",cap.dots);
   ok("an untouched capsule draws the top row",cap.dotsIn(TrioZones.Zone.TOP));
   ok("an untouched capsule draws the bottom row",cap.dotsIn(TrioZones.Zone.BOTTOM));
   // Out of ring releases the rows exactly as before.
   TrioSettings outside=legacy.copy();
   outside.signalMode=Prefs.SIGNAL_OUT_RING;
   ok("out of ring still releases both rows",
      !TrioAppearance.of(outside).capsule(79,false,-1,"5GA",2).dots);
  }
  // 2. The rows are placements: they can be removed, and each opening is independent.
  {
   TrioSettings cleared=TrioSettings.defaults();
   cleared.trioStyle=Prefs.STYLE_CAPSULE;
   // A reading in the interior and nothing in the openings: the user removed both rows.
   cleared.ringZones=Prefs.decodeLayout("|3|");
   TrioAppearance.Capsule cap=TrioAppearance.of(cleared).capsule(79,false,-1,"5GA",2);
   ok("a capsule with an interior reading draws no dots",!cap.dots);
   ok("and neither opening holds a row",
      !cap.dotsIn(TrioZones.Zone.TOP) && !cap.dotsIn(TrioZones.Zone.BOTTOM));
   ok("the interior reading survives",cap.parts[TrioZones.Zone.CENTRE.ordinal()]
      !=Prefs.PART_NONE);

   // SIM 1 in the top opening only: the two bands are independent.
   TrioSettings topOnly=TrioSettings.defaults();
   topOnly.trioStyle=Prefs.STYLE_CAPSULE;
   topOnly.showSim1=true;
   topOnly.ringZones=Prefs.decodeLayout("7|3|");
   TrioAppearance.Capsule top=TrioAppearance.of(topOnly).capsule(79,false,-1,"5GA",2);
   ok("a top-only placement draws the top row",top.dotsIn(TrioZones.Zone.TOP));
   ok("and leaves the bottom opening empty",!top.dotsIn(TrioZones.Zone.BOTTOM));
   ok("one row still counts as dots for the outline",top.dots);

   // The bottom opening is a placement target in its own right.
   TrioSettings bottomOnly=TrioSettings.defaults();
   bottomOnly.trioStyle=Prefs.STYLE_CAPSULE;
   bottomOnly.showSim2=true;
   bottomOnly.ringZones=Prefs.decodeLayout("|3|8");
   TrioAppearance.Capsule bot=TrioAppearance.of(bottomOnly).capsule(79,false,-1,"5GA",2);
   ok("a bottom-only placement draws the bottom row",bot.dotsIn(TrioZones.Zone.BOTTOM));
   ok("and leaves the top opening empty",!bot.dotsIn(TrioZones.Zone.TOP));
  }
  // 3. The editor can name what it sees: the occupant of a band holding dots is a piece the
  // picker offers, which is what makes the row editable rather than merely visible.
  {
   TrioSettings topOnly=TrioSettings.defaults();
   topOnly.trioStyle=Prefs.STYLE_CAPSULE;
   topOnly.showSim1=true;
   topOnly.ringZones=Prefs.decodeLayout("7|3|");
   TrioAppearance a=TrioAppearance.of(topOnly);
   ok("the top band reports an occupant",
      TrioZones.occupant(TrioZones.Zone.TOP,a,79,false,-1,"5GA",2)!=null);
   // And that band is offered the rows, so the user can put one there or take it away.
   java.util.List<TrioZones.Part> choices=
      TrioZones.placementChoices(TrioZones.Zone.TOP,topOnly,2);
   ok("the top band is offered the per-card rows",
      choices.contains(TrioZones.Part.SIM_1) || choices.contains(TrioZones.Part.SIM_2));
   // Both openings are bands the solver writes to.
   ok("a row can be placed in the top opening",
      TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_2,topOnly,2)!=null);
   ok("a row can be placed in the bottom opening",
      TrioZones.assign(TrioZones.Zone.BOTTOM,false,TrioZones.Part.SIM_1,topOnly,2)!=null);
   // Clearing a band that holds a row leaves it empty rather than falling back to the switch.
   TrioSettings emptied=TrioZones.withPiece(topOnly,TrioZones.Zone.TOP.ordinal(),null);
   ok("clearing the top opening removes its row",
      !TrioAppearance.of(emptied).capsule(79,false,-1,"5GA",2).dotsIn(TrioZones.Zone.TOP));
  }
  // 4. The capsule is slot-driven for the rows but still refuses what it cannot draw: the
  // clock is fixed in its own column and is not a placement.
  {
   TrioSettings s=TrioSettings.defaults();
   s.trioStyle=Prefs.STYLE_CAPSULE;
   ok("the capsule still refuses to place the clock",
      TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.CLOCK,s,2)==null);
   ok("the capsule answers hasSlots",TrioZones.hasSlots(s));
   TrioSettings rect=s.copy();rect.trioStyle=Prefs.STYLE_RECT;
   ok("the rectangle has no bands",!TrioZones.hasSlots(rect));
  }
  // 5. A reading in an opening straddles the outline, in the gap the dot row opened, and its
  // whole ink stays inside the box the glyph is fitted to.
  //
  // This is the "space design is wrong" defect, and it had two halves. The first was that the
  // cell sat in a lane INSIDE the capsule, stacked on the clock and the percentage the body
  // already draws there - so an opening was not a place of its own. The second was that even
  // there it could not be drawn large: the lane was measured against the strip the ink box
  // left beyond the stroke, which is about one stroke wide.
  //
  // The fix is that the opening is a real row. The reading is centred on the path line, and the
  // host grows by CAP_BAND_H above and below so the half of the line outside the capsule has
  // somewhere to land. The capsule itself does not change size: the fit is width-driven and the
  // width is untouched (pinned below), so every pixel of it is where it was.
  {
   for(int ringStroke=Prefs.MIN_RING_STROKE;ringStroke<=Prefs.MAX_RING_STROKE;ringStroke++) {
    float stroke=ringStroke*TrioGeometry.CAP_STROKE_RATIO;
    float room=TrioGeometry.capOpeningTextRoomH(stroke);
    float topCy=TrioGeometry.capOpeningCy(true,stroke);
    float bottomCy=TrioGeometry.capOpeningCy(false,stroke);
    // Centred on the path line: the label reads as the row that interrupted the outline.
    ok("stroke "+ringStroke+": the top cell is centred on the outline",
       Math.abs(topCy-TrioGeometry.CAP_TOP)<0.001f);
    ok("stroke "+ringStroke+": the bottom cell is centred on the outline",
       Math.abs(bottomCy-TrioGeometry.CAP_BOTTOM)<0.001f);
    // Its ink fits the banded box, so the host does not clip it.
    ok("stroke "+ringStroke+": the top cell stays inside the banded box",
       topCy-room*0.5f>=TrioGeometry.CAP_INK_TOP-0.001f);
    ok("stroke "+ringStroke+": the bottom cell stays inside the banded box",
       bottomCy+room*0.5f<=TrioGeometry.CAP_INK_BOTTOM+0.001f);
    // The CAPSULE's own box is unchanged. This is the compatibility pin: it is what keeps the
    // width asked of the status bar - and so the capsule's size in pixels - exactly as shipped,
    // because the fit is width-driven and CAP_INK_W is untouched by the bands.
    ok("stroke "+ringStroke+": the capsule box still ends at the outline plus its stroke",
       Math.abs((TrioGeometry.CAP_INK_Y+TrioGeometry.CAP_INK_H)
          -(TrioGeometry.CAP_BOTTOM+Prefs.MAX_RING_STROKE
            *TrioGeometry.CAP_STROKE_RATIO*0.5f))<0.001f);
    // The bands are exactly what was added, on both sides.
    ok("stroke "+ringStroke+": the banded box is the capsule box plus both bands",
       Math.abs(TrioGeometry.CAP_INK_TOP-(TrioGeometry.CAP_INK_Y-TrioGeometry.CAP_BAND_H))<0.001f
       && Math.abs(TrioGeometry.CAP_INK_BOTTOM
            -(TrioGeometry.CAP_INK_Y+TrioGeometry.CAP_INK_H+TrioGeometry.CAP_BAND_H))<0.001f);
   }
   // The openings no longer fight the body for space: a reading there is half outside the
   // capsule, so the two cannot overlap the clock in the middle.
   float stroke=Prefs.DEF_RING_STROKE*TrioGeometry.CAP_STROKE_RATIO;
   float room=TrioGeometry.capOpeningTextRoomH(stroke);
   ok("the opening readings sit clear of the body",
      TrioGeometry.capOpeningCy(true,stroke)+room*0.5f
      <= TrioGeometry.capInteriorTop(stroke)+0.5f);
   // And the host really does grow, in HEIGHT only - the width the status bar is asked for is
   // unchanged, which is the whole reason the capsule does not shrink.
   int bare=60;
   ok("the host grows taller", TrioGeometry.capHostHeightPx(bare)>bare);
   ok("the host width is untouched by the bands",
      TrioGeometry.capHostWidthPx(bare)==Math.round(
         bare*(TrioGeometry.capInkW()/TrioGeometry.CAP_INK_H)));
   ok("and the grown height keeps the same aspect the width was asked for",
      Math.abs((TrioGeometry.capHostHeightPx(bare)-bare)
         -2f*TrioGeometry.CAP_BAND_H*(bare/TrioGeometry.CAP_INK_H))<=1f);
  }
 }
 /**
  * The ring and the capsule keep their own components and layout.
  *
  * <p>Before the split both arrangements read and wrote the same three slots and the same six
  * switches, so setting up one silently reconfigured the other: placing the percentage in the
  * ring's middle and then switching to the capsule emptied one of the two. The fix moves the
  * component keys to a per-arrangement name (`show_wifi` for the ring, `show_wifi@capsule` for
  * the capsule) while leaving every size, weight and colour shared.
  *
  * <p>Three claims: the keys map as intended, an existing install inherits its ring values into
  * the capsule rather than getting defaults, and an edit to either arrangement leaves the other
  * untouched in BOTH directions - which is the property the user actually notices.
  */
 static void isolation() {
  // 1. The key mapping. The ring keeps the historical unsuffixed name, so no stored value
  //    has to move; only the capsule gets a new name.
  ok("the ring keeps the historical key",
     Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_RING).equals(Prefs.KEY_SHOW_WIFI));
  ok("the capsule gets its own key",
     Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE)
        .equals(Prefs.KEY_SHOW_WIFI+Prefs.STYLE_SUFFIX));
  ok("the layout is per arrangement",
     Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE)
        .equals(Prefs.KEY_RING_LAYOUT+Prefs.STYLE_SUFFIX));
  // Appearance is NOT per arrangement: a user who chose 40pt digits wants them in both.
  for(String key:new String[]{Prefs.KEY_RING_STROKE,Prefs.KEY_VALUE_SIZE,
      Prefs.KEY_TYPE_WEIGHT,Prefs.KEY_DOT_SCALE,Prefs.KEY_GLYPH_SCALE}) {
   ok("appearance stays shared: "+key,
      Prefs.styledKey(key,Prefs.STYLE_CAPSULE).equals(key));
  }
  ok("the base of a suffixed key is recovered",
     Prefs.styledBase(Prefs.KEY_RING_LAYOUT+Prefs.STYLE_SUFFIX).equals(Prefs.KEY_RING_LAYOUT));

  // 2. Migration: an install that only ever had ring values hands them to the capsule.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.KEY_SHOW_WIFI,false);
   prefs.m.put(Prefs.KEY_SHOW_VALUE,false);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule inherits the pre-split switch",!capsule.showWifi && !capsule.showValue);
   ok("the capsule inherits the pre-split layout",
      java.util.Arrays.equals(capsule.ringZones[0],new int[]{Prefs.PART_WIFI}));
  }

  // 3. Isolation, capsule edit first: the ring must be untouched.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.KEY_SHOW_WIFI,true);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   // The user switches to the capsule and edits it.
   prefs.m.put(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE),false);
   prefs.m.put(Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE),"||1");
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule reads its own switch",!capsule.showWifi);
   ok("the capsule reads its own layout",
      java.util.Arrays.equals(capsule.ringZones[2],new int[]{Prefs.PART_WIFI}));
   TrioSettings ring=TrioSettings.from(prefs,3f,Prefs.STYLE_RING);
   ok("the ring kept its switch after a capsule edit",ring.showWifi);
   ok("the ring kept its layout after a capsule edit",
      java.util.Arrays.equals(ring.ringZones[0],new int[]{Prefs.PART_WIFI}));
   ok("and the capsule layout did not leak upward",ring.ringZones[2].length==0);
  }

  // 4. And the other way: a ring edit must leave the capsule alone.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.styledKey(Prefs.KEY_SHOW_VALUE,Prefs.STYLE_CAPSULE),true);
   prefs.m.put(Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE),"||1");
   // The user goes back to the ring and changes it.
   prefs.m.put(Prefs.KEY_SHOW_VALUE,false);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   TrioSettings ring=TrioSettings.from(prefs,3f,Prefs.STYLE_RING);
   ok("the ring edit applied to the ring",!ring.showValue);
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule kept its own value switch",capsule.showValue);
   ok("the capsule kept its own layout",
      java.util.Arrays.equals(capsule.ringZones[2],new int[]{Prefs.PART_WIFI}));
   ok("and did not inherit the ring's new placement",
      !java.util.Arrays.equals(capsule.ringZones[0],new int[]{Prefs.PART_WIFI}));
  }

  // 5. The remote-preference and broadcast paths must speak the suffixed keys too, or a
  //    capsule edit would reach storage and never reach the hooked process.
  {
   TrioSettings snap=TrioSettings.defaults();
   snap.trioStyle=Prefs.STYLE_CAPSULE;
   snap.showWifi=false;
   TrioSettings target=TrioSettings.defaults();
   ok("a suffixed remote key writes its field",
      target.applyKeyFrom(snap,Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE))
      && !target.showWifi);
   android.os.Bundle bundle=snap.toBundle();
   ok("the capsule bundle carries its own key",
      bundle.containsKey(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE)));
   ok("and the value it carries",
      !bundle.getBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE),true));
   TrioSettings ringSnap=TrioSettings.defaults();
   ringSnap.trioStyle=Prefs.STYLE_RING;
   ok("the ring's bundle keeps the unsuffixed key",
      ringSnap.toBundle().containsKey(Prefs.KEY_SHOW_WIFI));
  }
 }
 /** Minimal in-memory prefs, so these cases exercise the real readers. */
 static final class Map implements android.content.SharedPreferences {
  final java.util.Map<String,Object> m=new java.util.HashMap<>();
  public boolean getBoolean(String k,boolean d){Object v=m.get(k);return v==null?d:(Boolean)v;}
  public int getInt(String k,int d){Object v=m.get(k);return v==null?d:(Integer)v;}
  public String getString(String k,String d){Object v=m.get(k);return v==null?d:(String)v;}
  public boolean contains(String k){return m.containsKey(k);}
  public android.content.SharedPreferences.Editor edit(){
   return new android.content.SharedPreferences.Editor(){
    final java.util.Map<String,Object> pending=new java.util.HashMap<>();
    public android.content.SharedPreferences.Editor putBoolean(String k,boolean v){
     pending.put(k,v);return this;}
    public android.content.SharedPreferences.Editor putInt(String k,int v){
     pending.put(k,v);return this;}
    public android.content.SharedPreferences.Editor putString(String k,String v){
     pending.put(k,v);return this;}
    public boolean commit(){m.putAll(pending);pending.clear();return true;}
   };
  }
 }
 /**
  * Every stored setting is a field the settings screen can actually see change.
  *
  * <p>The defect that put this here: the four capsule interior nudges were wired through
  * defaults(), from(), copy(), applyKeyFrom() and both bundle directions, and were then
  * left out of equals(). The settings page holds its snapshot in a mutableStateOf whose
  * default policy is structural equality, so a write that moved only a nudge produced a
  * value Compose considered identical and scheduled no recomposition: the thumb jumped
  * back, the number stayed put, and the glyph did move - because the hooked process reads
  * the same values through its own path. Dragging any OTHER slider made the page
  * recompose for a reason, and the stale rows caught up. That is the "it only updates
  * when I touch another slider" report, and nothing in the module could see it.
  *
  * <p>So this suite does not test the four fields. It tests the RULE that produced them:
  * every public instance field must be compared by equals() and folded into hashCode().
  * The next setting added to the snapshot is covered the moment it is declared, without
  * anyone remembering this list exists.
  */
 static void nudges() {
  // 1. The contract, over the real class rather than a hand-written list: every public
  //    scalar setting has to be able to produce a snapshot equals() calls different.
  {
   TrioSettings base=TrioSettings.defaults();
   java.lang.reflect.Field[] fields=TrioSettings.class.getFields();
   int compared=0;
   for(java.lang.reflect.Field f:fields) {
    int modifiers=f.getModifiers();
    if(!java.lang.reflect.Modifier.isPublic(modifiers)
       || java.lang.reflect.Modifier.isStatic(modifiers)) {
     continue;
    }
    // Editor routing is intentionally not a persisted appearance value.
    if (f.getName().equals("editingChargeScene")) {
     TrioSettings routed=base.copy();routed.editingChargeScene=ChargeLayout.CHARGING;
     ok("editor routing does not change persisted equality or hash",
        base.equals(routed) && base.hashCode()==routed.hashCode());
     ok("copy preserves editor routing",routed.copy().editingChargeScene==ChargeLayout.CHARGING);
     ok("wire snapshots omit editor routing",TrioSettings.fromBundle(routed.toBundle()).editingChargeScene==ChargeLayout.BASE);
     continue;
    }
    // Arrays are compared by deepEquals inside the class and are covered by their own
    // suites; this sweep is about the scalars, which are the ones a list can silently
    // stop growing.
    Class<?> t=f.getType();
    if(t==CapsuleLayoutSettings.class) {
     for(var component:CapsuleLayoutSettings.class.getRecordComponents()) {
      String name="capsule"+Character.toUpperCase(component.getName().charAt(0))+component.getName().substring(1);
      TrioSettings moved=base.copy();setNudge(moved,name,currentNudge(base,name)+1);
      ok("equals() sees a change in "+name,!base.equals(moved));compared++;
     }
     continue;
    }
    if(t!=int.class && t!=boolean.class) {
     continue;
    }
    TrioSettings moved=base.copy();
    try {
     if(t==boolean.class) {
      f.set(moved,Boolean.valueOf(!((Boolean)f.get(base)).booleanValue()));
     } else {
      // One away from the stored value, in whichever direction has room: an int that
      // sits on some ceiling still has to be distinguishable from its neighbour.
      int now=f.getInt(base);
      f.set(moved,Integer.valueOf(now+1));
      if(now+1>9999) {
       f.set(moved,Integer.valueOf(now-1));
      }
     }
    } catch(IllegalAccessException ex) {
     throw new AssertionError("cannot move "+f.getName(),ex);
    }
    ok("equals() sees a change in "+f.getName(),!base.equals(moved));
    compared++;
   }
   ok("the sweep reached the whole snapshot",compared>=40);
   // The other half of the contract: values equals() calls the same must hash the same.
   TrioSettings again=TrioSettings.defaults();
   ok("equal snapshots agree on their hash",base.equals(again)
      && base.hashCode()==again.hashCode());
  }
  // 2. The four nudges by name, so a rename cannot quietly drop them from the sweep.
  {
   TrioSettings s=TrioSettings.defaults();
   ok("a fresh install does not nudge",s.capsuleLayout.leftDx()==Prefs.DEF_CAPSULE_NUDGE
      && s.capsuleLayout.leftDy()==Prefs.DEF_CAPSULE_NUDGE
      && s.capsuleLayout.rightDx()==Prefs.DEF_CAPSULE_NUDGE
      && s.capsuleLayout.rightDy()==Prefs.DEF_CAPSULE_NUDGE);
   for(String name:new String[]{"capsuleLeftDx","capsuleLeftDy","capsuleRightDx","capsuleRightDy"}) {
    TrioSettings moved=s.copy();
    setNudge(moved,name,3);
    ok(name+" is a stored setting, not a derived one",!s.equals(moved));
    ok(name+" survives copy()",currentNudge(moved.copy(),name)==3);
    ok(name+" survives the bundle",currentNudge(
       TrioSettings.fromBundle(moved.toBundle()),name)==3);
    ok(name+" is applied from the remote key",
       applyNudgeKey(name,prefKeyFor(name),s));
   }
   // 3. The clamp is on both doors: the stored file and the broadcast extras.
   Map prefs=new Map();
   prefs.m.put(Prefs.KEY_CAPSULE_LEFT_DX,Integer.valueOf(9999));
   prefs.m.put(Prefs.KEY_CAPSULE_LEFT_DY,Integer.valueOf(-9999));
   ok("a wild x nudge is clamped on read",
      TrioSettings.from(prefs,3f).capsuleLayout.leftDx()==Prefs.MAX_CAPSULE_NUDGE_X);
   ok("a wild y nudge is clamped on read",
      TrioSettings.from(prefs,3f).capsuleLayout.leftDy()==Prefs.MIN_CAPSULE_NUDGE);
   android.os.Bundle wild=new android.os.Bundle();
   wild.putInt(Prefs.KEY_CAPSULE_RIGHT_DX,9999);
   // The RIGHT cell has its own mirrored span, so a wild value lands on ITS ceiling.
   ok("the broadcast extras are clamped too",
      TrioSettings.fromBundle(wild).capsuleLayout.rightDx()==Prefs.MAX_CAPSULE_NUDGE_X_RIGHT);
   // 4. The appearance carries them, since the renderer reads that view of the snapshot.
   TrioSettings nudged=TrioSettings.defaults();nudged.capsuleLayout=nudged.capsuleLayout.withRightDy(5);
   ok("the renderer sees the nudge",TrioAppearance.of(nudged).capsuleRightDy==5);
  }
  // 5. issue 45's flag, found by the same audit and left out of equals() too.
  {
   TrioSettings s=TrioSettings.defaults();
   TrioSettings on=s.copy();on.perfTempChargingOnly=true;
   ok("the charging-only temperature participates in equality",!s.equals(on));
  }
  // 12. A charger can put the number in the ring centre (issue 34).
  {
   // The request: "replace whatever is displayed inside the ring with the battery percentage
   // while charging, and return to the previous display when charging stops."
   TrioSettings s = TrioSettings.defaults();
   ok("the centre keeps its own content by default", !s.chargingShowsValue);
   s.trioStyle = Prefs.STYLE_RING;
   TrioSettings on = s.copy();
   on.chargingShowsValue = true;
   ok("the setting reaches the appearance", TrioAppearance.of(on).chargingShowsValue);
   ok("and copy() carries it", on.copy().chargingShowsValue);
   ok("and the bundle round-trips it",
      TrioSettings.fromBundle(on.toBundle()).chargingShowsValue);
   ok("and the remote key applies it",
      TrioSettings.defaults().applyKeyFrom(on, Prefs.KEY_CHARGING_SHOWS_VALUE));
   ok("it participates in equality", !s.equals(on));
   // The override is per frame: charging must CHANGE the centre, unplugged must not, and the
   // stored layout must never be touched - a charger that rewrote the arrangement would have
   // to be undone on unplug, losing whatever had been there.
   // The centre refuses signal rows (Prefs.zoneAccepts), so what sits there is the arcs -
   // a piece the ring does allow in the middle, and one whose ink only needs a live level.
   on.ringZones[TrioZones.Zone.CENTRE.ordinal()] = new int[] { Prefs.PART_WIFI };
   final TrioAppearance ap = TrioAppearance.of(on);
   ok("charging substitutes the percentage",
      ap.ring(66, true, 3, "5G", 1).parts[TrioZones.Zone.CENTRE.ordinal()]
      == Prefs.PART_VALUE);
   ok("unplugged leaves the layout alone",
      ap.ring(66, false, 3, "5G", 1).parts[TrioZones.Zone.CENTRE.ordinal()]
      == Prefs.PART_WIFI);
   // Negative control: the SAME layout with the switch off must keep the arcs in the middle,
   // otherwise the assertion above could pass for the wrong reason.
   final TrioSettings off = on.copy();
   off.chargingShowsValue = false;
   ok("with the switch off the layout keeps the centre",
      TrioAppearance.of(off).ring(66, true, 3, "5G", 1)
        .parts[TrioZones.Zone.CENTRE.ordinal()] == Prefs.PART_WIFI);
   // Only the centre: the openings are why the ring was opened, and a mouth holding a
   // different piece would change the outline as a side effect of a preference.
   ok("the openings are untouched",
      ap.ring(66, true, 3, "5G", 1).parts[0]
      == ap.ring(66, false, 3, "5G", 1).parts[0]);
  }
  // 11. The network type can step aside while Wi-Fi is up (issue 48).
  {
   TrioSettings s = TrioSettings.defaults();
   ok("the label stays put by default", !s.typeHideOnWifi
      && !Prefs.DEF_TYPE_HIDE_ON_WIFI);
   // The flag reaches the appearance, which is what the renderer consults.
   TrioSettings hiding = s.copy();
   hiding.typeHideOnWifi = true;
   ok("the flag survives appearance resolution",
      TrioAppearance.of(hiding).typeHideOnWifi);
   ok("and copy() carries it", hiding.copy().typeHideOnWifi);
   ok("and the bundle round-trips it",
      TrioSettings.fromBundle(hiding.toBundle()).typeHideOnWifi);
   ok("and the remote key applies it",
      TrioSettings.defaults().applyKeyFrom(hiding, Prefs.KEY_TYPE_HIDE_ON_WIFI));
   ok("it participates in equality", !s.equals(hiding));
  }
  // 10. The Wi-Fi arcs have a size of their own, and it reaches the canvas.
  {
   // Before this there was no way to ask for larger arcs: the fit was derived from the
   // stroke alone, so a user who found them small could only thicken the line. The bolt and
   // the dot rows already had a percentage, so this follows that shape rather than inventing
   // a second one.
   TrioSettings s = TrioSettings.defaults();
   ok("arcs are at the fit size by default", s.wifiScale == Prefs.DEF_WIFI_SCALE);
   ok("and the default is a no-op on the drawing",
      TrioAppearance.of(s).wifiScaleFactor() == 1f);
   // The factor has to move with the setting, or the slider would be a number nothing reads.
   TrioSettings bigger = s.copy();
   bigger.wifiScale = Prefs.MAX_WIFI_SCALE;
   ok("a larger setting scales the arcs up",
      TrioAppearance.of(bigger).wifiScaleFactor() > 1f);
   TrioSettings smaller = s.copy();
   smaller.wifiScale = Prefs.MIN_WIFI_SCALE;
   ok("a smaller one scales them down",
      TrioAppearance.of(smaller).wifiScaleFactor() < 1f);
   // And it must survive every transport, or the status bar and the preview would disagree.
   ok("it survives copy()", bigger.copy().wifiScale == Prefs.MAX_WIFI_SCALE);
   ok("it survives the bundle",
      TrioSettings.fromBundle(bigger.toBundle()).wifiScale == Prefs.MAX_WIFI_SCALE);
   ok("it is applied from the remote key",
      s.applyKeyFrom(bigger, Prefs.KEY_WIFI_SCALE) && s.wifiScale == Prefs.MAX_WIFI_SCALE);
   // Out of range values from a stale build are clamped here, where they reach the canvas.
   TrioSettings wild = TrioSettings.defaults();
   wild.wifiScale = 9999;
   ok("a wild setting is clamped to the ceiling",
      TrioAppearance.of(wild).wifiScaleFactor() == Prefs.MAX_WIFI_SCALE / 100f);
  }
  // 9. A capsule opening's guide is narrower than its hit region.
  {
   // The hit region for an opening spans the whole ink box, so a press anywhere along the top
   // of the capsule opens that band - right for a finger, wrong as a picture. The strip the
   // band DRAWS in is the flat run between the two caps, where the beads sit, which is less
   // than two thirds of that width. Outlining the hit region drew a box from one end of the
   // capsule to the other, well past the dots it named.
   TrioSettings s = TrioSettings.defaults();
   s.trioStyle = Prefs.STYLE_CAPSULE;
   final TrioAppearance a = TrioAppearance.of(s);
   for (TrioZones.Zone band : new TrioZones.Zone[] {
           TrioZones.Zone.TOP, TrioZones.Zone.BOTTOM }) {
    final float[][] hit = TrioZones.boxes(band, a);
    final float[][] guide = TrioZones.highlightBoxes(band, a);
    ok(band + " still has a hit region", hit.length > 0 && guide.length > 0);
    final float hitW = hit[0][2] - hit[0][0];
    final float guideW = guide[0][2] - guide[0][0];
    ok(band + "'s guide is narrower than its hit region: " + guideW + " vs " + hitW,
       guideW < hitW);
    // Sized to the BEAD ROW, not to the straight run between the caps: four beads cover about
    // 40 units while the run is 79, so a guide built on the run is more than twice too wide -
    // which is what "the numbers are off by a lot" described.
    final float rowW = (TrioGeometry.CAP_DOT_PITCH * 1.5f + TrioGeometry.CAP_DOT_R) * 2f;
    ok(band + "'s guide is the bead row plus a margin, not the whole run: " + guideW,
       guideW >= rowW && guideW < rowW + TrioGeometry.CAP_DOT_R * 3f);
    // And it has to COVER the row vertically: the beads ride the outline's centreline, so
    // their ink reaches stroke/2 + radius to either side of it. A guide that stopped at the
    // line covered the outer half of the dots and missed the inner one.
    final boolean top = band == TrioZones.Zone.TOP;
    final float line = top ? TrioGeometry.CAP_TOP : TrioGeometry.CAP_BOTTOM;
    final float reach = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO * 0.5f
            + TrioGeometry.CAP_DOT_R;
    final float near = top ? guide[0][3] : guide[0][1];
    final float far = top ? guide[0][1] : guide[0][3];
    ok(band + "'s guide reaches past the beads on the outline side",
       Math.abs(near - line) >= reach);
    ok(band + "'s guide reaches past the beads on the outer side",
       Math.abs(far - line) >= reach);
   }
  }
  // 8. The capsule's two interior cells are independent components on one track.
  {
   // The model this replaced divided the interior between the cells in proportion to their
   // strings, so placing the clock on the left shrank the number on the right - the two
   // squeezed each other, and the press highlight changed width with whatever a cell was
   // showing. Each cell is now sized by its OWN reading and hangs off its own edge.
   final float strokeN = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
   TrioSettings seamSettings=TrioSettings.defaults();seamSettings.trioStyle=Prefs.STYLE_CAPSULE;
   final TrioAppearance seamAppearance=TrioAppearance.of(seamSettings);
   // One track, not a share: the width a reading is fitted to cannot depend on its neighbour.
   final float track = TrioGeometry.capReadingTrackWidth(strokeN);
   ok("a reading is fitted to the whole track",
      Math.abs(track - (TrioGeometry.capTextRight(strokeN)
         - TrioGeometry.capTextLeft(strokeN) - TrioGeometry.CAP_TEXT_GAP)) < 0.01f);
   // The two anchors are the interior's own edges, and neither is computed from a width.
   ok("the left cell hangs off the interior's left edge",
      Math.abs(TrioGeometry.capReadingEdge(strokeN, true)
         - TrioGeometry.capTextLeft(strokeN)) < 0.01f);
   ok("the right cell hangs off the interior's right edge",
      Math.abs(TrioGeometry.capReadingEdge(strokeN, false)
         - TrioGeometry.capTextRight(strokeN)) < 0.01f);
   // The two anchors are as far apart as the track plus the gap: nothing between them is
   // reserved for either cell, which is what "independent" means here.
   ok("nothing between them is reserved for either cell",
      Math.abs((TrioGeometry.capReadingEdge(strokeN, false)
                - TrioGeometry.capReadingEdge(strokeN, true))
               - (track + TrioGeometry.CAP_TEXT_GAP)) < 0.01f);
   // The zone seam sits at the track's midpoint, so a press resolves to the half it lands in
   // rather than to whichever cell happens to be wider.
   ok("the editor's seam is the track's midpoint",
      Math.abs(TrioZones.boxes(TrioZones.Zone.LEFT, seamAppearance)[0][2]
               - (TrioGeometry.capTextLeft(strokeN) + TrioGeometry.capReadingsWidth(strokeN) / 2f)) < 0.01f
      && Math.abs(TrioZones.boxes(TrioZones.Zone.CENTRE, seamAppearance)[0][0]
               - (TrioGeometry.capTextLeft(strokeN) + TrioGeometry.capReadingsWidth(strokeN) / 2f)) < 0.01f);  }
  // 7. A capsule offset must mean the SAME thing in the preview and on the status bar.
  {
   // The defect: the interior was divided by the strings actually drawn, and the settings
   // preview runs a fixed scene while the bar shows live readings. Same stored offset, two
   // different pictures - a style that cannot be reproduced in the preview is one the user
   // cannot configure. The split is now keyed off the KIND of piece, so it is a function of
   // the layout alone.
   TrioSettings s1=TrioSettings.defaults();
   s1.trioStyle=Prefs.STYLE_CAPSULE;
   s1.ringZones=Prefs.decodeLayout("||"+Prefs.PART_VALUE+"|"+Prefs.PART_TYPE);
   TrioSettings s2=s1.copy();
   // Same layout, completely different live readings - the sort of difference the preview
   // and the bar genuinely have.
   s1.valueSize=50; s2.valueSize=50;
   ok("the layout, not the reading, decides the split",
      java.util.Arrays.deepEquals(TrioAppearance.of(s1).ringZones,
                                  TrioAppearance.of(s2).ringZones));
   // A different PIECE must change the split, or the fix went too far and made it constant.
   TrioSettings s3=TrioSettings.defaults();
   s3.trioStyle=Prefs.STYLE_CAPSULE;
   s3.ringZones=Prefs.decodeLayout("||"+Prefs.PART_VALUE+"|"+Prefs.PART_WIFI);
   ok("a different piece in the left cell is a different layout",
      !java.util.Arrays.equals(TrioAppearance.of(s1).ringZones[3],
                               TrioAppearance.of(s3).ringZones[3]));
  }
  // 6. Each capsule cell has the horizontal travel its own edge implies.
  {
   // The cells lean outwards, so each travels INTO the interior and barely past its own
   // edge. Sharing one span handed both of them most of their travel in the direction they
   // cannot use, which is the "the slider moves a bit then stops" report.
   // Both spans are symmetric and equal: neither end is bounded by the outline any more,
   // because the draw is not clipped to it and overhanging a reading is a thing users ask
   // for. What has to hold is that each cell can travel a real distance in BOTH directions -
   // the earlier cuts failed that by giving most of the span to one side.
   ok("the left cell travels both ways",
      Prefs.MIN_CAPSULE_NUDGE_X < -10 && Prefs.MAX_CAPSULE_NUDGE_X > 10);
   ok("and so does the right cell",
      Prefs.MIN_CAPSULE_NUDGE_X_RIGHT < -10 && Prefs.MAX_CAPSULE_NUDGE_X_RIGHT > 10);
   ok("the two spans are equal",
      Prefs.MIN_CAPSULE_NUDGE_X == Prefs.MIN_CAPSULE_NUDGE_X_RIGHT
      && Prefs.MAX_CAPSULE_NUDGE_X == Prefs.MAX_CAPSULE_NUDGE_X_RIGHT);
   // ...and wider than the safe interior, so a reading really can overhang the outline.
   ok("the span reaches past the interior's own margin",
      Prefs.MIN_CAPSULE_NUDGE_X <= -20 && Prefs.MAX_CAPSULE_NUDGE_X >= 20);
   // Both cells start at zero, which is inside both spans - a default outside its own
   // slider range would be a row that cannot represent its own value.
   ok("zero is inside both horizontal spans",
      Prefs.MIN_CAPSULE_NUDGE_X < 0 && Prefs.MAX_CAPSULE_NUDGE_X > 0
      && Prefs.MIN_CAPSULE_NUDGE_X_RIGHT < 0 && Prefs.MAX_CAPSULE_NUDGE_X_RIGHT > 0);
   // The readers clamp each cell to ITS OWN span on both doors: a value the right slider
   // can produce must survive the stored read and the broadcast read, or it snaps back.
   Map right=new Map();
   right.m.put(Prefs.KEY_CAPSULE_RIGHT_DX,Integer.valueOf(Prefs.MIN_CAPSULE_NUDGE_X_RIGHT));
   ok("the stored read keeps the right cell at its own minimum",
      TrioSettings.from(right,3f).capsuleLayout.rightDx()==Prefs.MIN_CAPSULE_NUDGE_X_RIGHT);
   android.os.Bundle rightBundle=new android.os.Bundle();
   rightBundle.putInt(Prefs.KEY_CAPSULE_RIGHT_DX,Prefs.MIN_CAPSULE_NUDGE_X_RIGHT);
   ok("and so does the broadcast read",
      TrioSettings.fromBundle(rightBundle).capsuleLayout.rightDx()==Prefs.MIN_CAPSULE_NUDGE_X_RIGHT);
   // While a value only the LEFT span allows is trimmed for the right cell, which is what
   // keeps the two from silently sharing a range again.
   Map shared=new Map();
   shared.m.put(Prefs.KEY_CAPSULE_RIGHT_DX,Integer.valueOf(Prefs.MAX_CAPSULE_NUDGE_X));
   ok("a left-only value is trimmed for the right cell",
      TrioSettings.from(shared,3f).capsuleLayout.rightDx()==Prefs.MAX_CAPSULE_NUDGE_X_RIGHT);
  }
 }
 /** Writes one of the four nudge fields by name, so a rename fails loudly here. */
 static void setNudge(TrioSettings s,String name,int value) {
  try {
   var components=CapsuleLayoutSettings.class.getRecordComponents();
   Class<?>[] types=new Class<?>[components.length];Object[] values=new Object[components.length];boolean found=false;
   for(int i=0;i<components.length;i++) {
    var component=components[i];types[i]=component.getType();
    String logical="capsule"+Character.toUpperCase(component.getName().charAt(0))+component.getName().substring(1);
    values[i]=component.getAccessor().invoke(s.capsuleLayout);
    if(logical.equals(name)){values[i]=value;found=true;}
   }
   if(!found)throw new AssertionError("missing field "+name);
   s.capsuleLayout=CapsuleLayoutSettings.class.getDeclaredConstructor(types).newInstance(values);
  } catch(ReflectiveOperationException e) { throw new AssertionError("missing field "+name,e); }
 }
 /**
  * The storage key a nudge field is written under, read off the Prefs constant so this
  * cannot drift from the class it describes: capsuleLeftDx -> KEY_CAPSULE_LEFT_DX.
  */
 static String prefKeyFor(String field) {
  String constant="KEY_"+field.replaceAll("([A-Z])","_$1").toUpperCase(java.util.Locale.ROOT);
  try { return (String)Prefs.class.getField(constant).get(null); }
  catch(Exception e) { throw new AssertionError("missing constant "+constant,e); }
 }
 /**
  * Drives the remote-preference path: TrioConfig applies ONE key at a time, so a key that
  * applyKeyFrom() does not recognise silently repaints nothing.
  */
 static boolean applyNudgeKey(String field,String key,TrioSettings src) {
  TrioSettings moved=src.copy();
  setNudge(moved,field,3);
  TrioSettings into=src.copy();
  return into.applyKeyFrom(moved,key) && currentNudge(into,field)==3;
 }
 /** Reads one of the four nudge fields by name, so a rename fails loudly here. */
 static int currentNudge(TrioSettings s,String name) {
  try {
   String component=Character.toLowerCase(name.charAt(7))+name.substring(8);
   return (Integer)CapsuleLayoutSettings.class.getMethod(component).invoke(s.capsuleLayout);
  } catch(ReflectiveOperationException e) { throw new AssertionError("missing field "+name,e); }
 }
 public static void main(String[] args)throws Throwable {
  resetFixture();
  switch(args[0]){case "signal":signal();break;case "tint":tint();break;case "assignment":assignment();break;case "wifi":wifi();break;case "clear":clear();break;case "clock":clock();break;case "centre":centre();break;case "dots":dots();break;case "outorder":outorder();break;case "capsule":capsule();break;case "isolation":isolation();break;case "nudges":nudges();break;default:throw new AssertionError(args[0]);}
  System.out.println("PASS: "+args[0]+" / "+checks+" assertions");
 }
}
