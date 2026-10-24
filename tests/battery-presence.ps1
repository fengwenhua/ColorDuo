$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}
$hooksPath="$root/app/src/main/java/io/github/fengwenhua/colorduo/TrioHooks.java"
$battPath="$root/app/src/main/java/io/github/fengwenhua/colorduo/BatteryPresence.java"
$rendPath="$root/app/src/main/java/io/github/fengwenhua/colorduo/TrioRenderer.java"
$hooks=Get-Content $hooksPath -Raw
$batt=Get-Content $battPath -Raw
$rend=Get-Content $rendPath -Raw

# The real production methods, spliced in unchanged. If any of them is renamed
# or re-shaped the extraction fails loudly rather than testing a stale copy.
$wattsOf=[regex]::Match($batt,'(?s)private static float wattsOf\(int currentMicroAmps, int voltageMilliVolts\) \{.*?\n    \}').Value
if(!$wattsOf){throw 'Production wattsOf missing'}
$publish=[regex]::Match($batt,'(?s)private static void publish\(boolean attached, float watts, int temperature\) \{.*?\n    \}').Value
if(!$publish){throw 'Production publish missing'}
$perfText=[regex]::Match($hooks,'(?s)private static String perfText\(TrioAppearance a\) \{.*?\n    \}').Value
if(!$perfText){throw 'Production perfText missing'}
$powerText=[regex]::Match($hooks,'(?s)private static String powerText\(float watts\) \{.*?\n    \}').Value
if(!$powerText){throw 'Production powerText missing'}
$perfLines=[regex]::Match($hooks,'(?s)private static int perfLines\(String text\) \{.*?\n    \}').Value
if(!$perfLines){throw 'Production perfLines missing'}

# The separator, read out of the source rather than restated here. The separator
# IS the feature - the two readings share one view and sit on separate lines only
# because this character is a break - so a copy of it in this script would keep
# passing after the join was turned back into a space. The matched text is the
# Java escape as written (the two characters \ and n), which is exactly what has
# to go inside the char literal emitted below.
$perfBreak=[regex]::Match($hooks,"static final char PERF_BREAK = '(.+?)';").Groups[1].Value
if(!$perfBreak){throw 'could not read PERF_BREAK from TrioHooks'}

# The sentinels and the two gates have to agree with the production source, or
# the harness would pass on a contract the app no longer honours.
if($batt -notmatch 'return TrioState\.POWER_UNKNOWN;'){throw 'wattsOf no longer returns the power sentinel'}
# The sign of current_now is not portable: this platform answers negative while
# charging, so the reading is the magnitude. Without Math.abs the probe below
# would be asserting a shape the source does not have.
if($batt -notmatch 'Math\.abs\(currentMicroAmps\)'){throw 'wattsOf no longer takes the magnitude of the current'}
if($batt -match 'if \(!\(watts > 0f\)\)'){throw 'wattsOf still clamps a negative current to zero'}
if($batt -notmatch 'Float\.compare\(beforeWatts, TrioState\.sChargePowerW\) != 0'){throw 'publish no longer de-duplicates NaN-safe'}
# isNaN, not != : the sentinel is a NaN, and NaN != NaN is true, so an
# inequality gate would admit the very value it exists to exclude.
if($perfText -notmatch '!Float\.isNaN\(TrioState\.sChargePowerW\)'){throw 'perfText no longer gates on the power sentinel'}
if($perfText -match 'sChargePowerW != TrioState\.POWER_UNKNOWN'){throw 'perfText gates on the sentinel with != , which a NaN defeats'}
# Scoped to the readout's own constructor: setSingleLine(true) is still correct
# for the out-of-ring type label, which is always one line, so a file-wide
# search would forbid something that has to stay.
$createPerf=[regex]::Match($hooks,'(?s)private static PerfLabel createPerfLabel\(ViewGroup container\) \{.*?\n    \}').Value
if(!$createPerf){throw 'Production createPerfLabel missing'}
# Comments are stripped before the negative check: the body's own comment
# explains why setSingleLine is NOT used, and matching that prose would forbid
# the very thing the comment is defending.
$createPerfCode=($createPerf -split "`n" | Where-Object { $_ -notmatch '^\s*//' }) -join "`n"
if($createPerfCode -notmatch 'setMaxLines\(2\)'){throw 'the readout must allow two lines'}
if($createPerfCode -match 'setSingleLine'){throw 'the readout must not be pinned to one line'}

$out="$root/work/batterycheck/tests"; New-Item -ItemType Directory -Force $out | Out-Null
# Everything here is ASCII: the degree sign is written the way the production
# file writes it, as an escape, so this script itself needs no encoding.
$test=@"
public class BatteryTest {
 static class TrioState {
  static final int TEMP_UNKNOWN = Integer.MIN_VALUE;
  static final float POWER_UNKNOWN = Float.NaN;
  static boolean sChargingAttached = false;
  static float sChargePowerW = 0f;
  static int sBatteryTempC = TEMP_UNKNOWN;
 }
 static class TrioHooks { static int invalidations; static void invalidateHosts(){ invalidations++; } }
 static class TrioAppearance {
  boolean perfPower; boolean perfTemp;
  boolean perfAnywhere(){ return perfPower || perfTemp; }
 }
 static final char PERF_BREAK = '$perfBreak';

 $wattsOf
 $publish
 $powerText
 $perfText
 $perfLines

 static TrioAppearance cfg(boolean power, boolean temp){
  TrioAppearance a = new TrioAppearance(); a.perfPower = power; a.perfTemp = temp; return a;
 }
 static int checks;
 static void eq(String what, Object got, Object want){
  checks++;
  if(!String.valueOf(got).equals(String.valueOf(want))){
   throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
  }
 }

 public static void main(String[] args){
  // ---- wattsOf: the arithmetic, the sign, and the two ways of not knowing.
  // The device's own observed numbers: 44000 uA at 4381 mV is 0.19 W.
  eq("live charging current", Math.round(wattsOf(44000, 4381) * 100), 19L);
  eq("typical fast charge", Math.round(wattsOf(2500000, 4400) * 10), 110L);
  // The exact shape this phone reports WHILE CHARGING, measured on it:
  // current_now = -204000 uA at 4008 mV with USB powered and status charging.
  // The sign is not portable, so the magnitude is the reading; treating the
  // minus as "discharging" is what pinned the status bar on 0.0W.
  eq("charging answers negative here", Math.round(wattsOf(-204000, 4008) * 100), 82L);
  eq("a negative current is the same watts as its magnitude",
     wattsOf(-204000, 4008), wattsOf(204000, 4008));
  eq("the 100 percent shape is no longer zero", Math.round(wattsOf(-164000, 4400) * 100), 72L);
  eq("zero current is a real zero", wattsOf(0, 4400), 0f);
  // NaN is never equal to itself, so the sentinel is probed with isNaN.
  eq("unknown current", Float.isNaN(wattsOf(Integer.MIN_VALUE, 4400)), true);
  eq("absent voltage", Float.isNaN(wattsOf(1000000, 0)), true);
  eq("negative voltage", Float.isNaN(wattsOf(1000000, -1)), true);
  // A real zero and the sentinel must be distinguishable, or the two readings
  // below are the same string and the distinction is decorative.
  eq("zero is not the sentinel", Float.isNaN(wattsOf(0, 4400)), false);

  // ---- perfText: the segment rules.
  eq("both off", perfText(cfg(false, false)), "");
  eq("power on, nothing attached", perfText(cfg(true, false)), "");
  // The documented promise: no charger drops the segment instead of a 0W.
  TrioState.sChargingAttached = false; TrioState.sChargePowerW = 6.1f;
  eq("attached false hides power", perfText(cfg(true, false)), "");
  // Attached but the platform would not answer: the regression this test is
  // for. Before the sentinel was honoured this rendered "0.0W".
  TrioState.sChargingAttached = true; TrioState.sChargePowerW = TrioState.POWER_UNKNOWN;
  eq("attached but unknown hides power", perfText(cfg(true, false)), "");
  eq("attached but unknown, with temp", perfText(cfg(true, true)) , "");
  // A genuine zero is still drawn: the charger has stopped pushing, and the
  // reading must not blink out at recharge-full.
  TrioState.sChargePowerW = 0f;
  eq("attached at a real zero", perfText(cfg(true, false)), "0.0W");
  TrioState.sChargePowerW = 6.1f;
  eq("under ten keeps a decimal", perfText(cfg(true, false)), "6.1W");
  TrioState.sChargePowerW = 18f;
  eq("ten and up is whole", perfText(cfg(true, false)), "18W");
  TrioState.sChargePowerW = 9.95f;
  eq("rounds to ten", perfText(cfg(true, false)), "10W");
  TrioState.sChargePowerW = 0.04f;
  eq("tiny stays one decimal", perfText(cfg(true, false)), "0.0W");

  // ---- perfText: the pairing, one reading per line.
  TrioState.sChargePowerW = 6.1f; TrioState.sBatteryTempC = 31;
  eq("both on", perfText(cfg(true, true)), "6.1W" + PERF_BREAK + "31" + "\u00B0C");
  eq("both on is two lines", perfLines(perfText(cfg(true, true))), 2);
  eq("temp only", perfText(cfg(false, true)), "31" + "\u00B0C");
  eq("temp only is one line", perfLines(perfText(cfg(false, true))), 1);
  eq("power only is one line", perfLines(perfText(cfg(true, false))), 1);
  // No charger with both switches on: the temperature must not inherit a
  // leading break and the view must stay one line tall.
  TrioState.sChargingAttached = false;
  eq("temp alone after power drops out", perfText(cfg(true, true)), "31" + "\u00B0C");
  eq("and it is one line", perfLines(perfText(cfg(true, true))), 1);
  // Unknown temperature must never be formatted as a number.
  TrioState.sChargingAttached = true; TrioState.sBatteryTempC = TrioState.TEMP_UNKNOWN;
  eq("unknown temp drops its segment", perfText(cfg(true, true)), "6.1W");
  eq("power alone after temp drops out", perfLines(perfText(cfg(true, true))), 1);
  eq("both unknown", perfText(cfg(false, true)), "");
  eq("empty text is one line", perfLines(perfText(cfg(false, false))), 1);
  // The separator's value is locked here rather than trusted: every two-line
  // case above is built from it, so a change back to a space would silently turn
  // them all into one-line cases.
  eq("the two readings are separated by a line break", (int) PERF_BREAK, (int) '\n');
  // The string must never open or close with a separator.
  eq("no leading break", perfText(cfg(false, true)).startsWith("" + PERF_BREAK), false);
  eq("no trailing break", perfText(cfg(false, true)).endsWith("" + PERF_BREAK), false);

  // ---- publish: the change test, including the NaN trap.
  // Seeded away from the first call so it is a change: the point of the first
  // two checks is the de-duplication, not the initial state.
  TrioState.sChargingAttached = true; TrioState.sChargePowerW = 5f;
  TrioState.sBatteryTempC = 30; TrioHooks.invalidations = 0;
  publish(false, 0f, TrioState.TEMP_UNKNOWN);
  eq("first publish repaints", TrioHooks.invalidations, 1);
  publish(false, 0f, TrioState.TEMP_UNKNOWN);
  eq("an unchanged publish does not", TrioHooks.invalidations, 1);
  publish(true, 6.1f, 31);
  eq("a real change repaints", TrioHooks.invalidations, 2);
  publish(true, 6.1f, 31);
  eq("the same reading does not", TrioHooks.invalidations, 2);
  publish(true, 6.2f, 31);
  eq("watts moving repaints", TrioHooks.invalidations, 3);
  publish(true, 6.2f, 32);
  eq("temperature moving repaints", TrioHooks.invalidations, 4);
  publish(false, 6.2f, 32);
  eq("detaching repaints", TrioHooks.invalidations, 5);
  // The NaN trap: NaN != NaN is true, so a plain comparison would call every
  // frame a change and repaint the status bar forever on a device that cannot
  // report power.
  publish(true, TrioState.POWER_UNKNOWN, 31);
  eq("unknown power repaints once", TrioHooks.invalidations, 6);
  publish(true, TrioState.POWER_UNKNOWN, 31);
  eq("and not again while it stays unknown", TrioHooks.invalidations, 6);
  publish(true, TrioState.POWER_UNKNOWN, 31);
  eq("still not", TrioHooks.invalidations, 6);

  System.out.println("PASS: " + checks + " cases over wattsOf, perfText, perfLines and publish");
 }
}
"@
[System.IO.File]::WriteAllText("$out/BatteryTest.java", $test, (New-Object System.Text.UTF8Encoding($false)))
& $javac -nowarn -d $out "$out/BatteryTest.java"; if($LASTEXITCODE){throw 'Compilation failed'}
& $java -Xmx64m -cp $out BatteryTest; if($LASTEXITCODE){throw 'Test failed'}
