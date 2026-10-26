param([string]$SourceDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$SourceDirectory) { $SourceDirectory = Join-Path $root 'app/src/main/java/io/github/fengwenhua/colorduo' }
$jdk = Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac = Join-Path $jdk 'javac.exe'; $java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }
$out = Join-Path $root 'work/ring-handoff'
$classes = Join-Path $out 'classes'
$viewDir = Join-Path $out 'android/view'
New-Item -ItemType Directory -Force $classes, $viewDir | Out-Null
$hooks = [IO.File]::ReadAllText((Join-Path $SourceDirectory 'TrioHooks.java'))
$methods = @()
foreach ($signature in @('private static List<String> foldedSlots()', 'private static void hideIcon(final View icon)',
        'private static void blockIcons(Object bar)', 'private static void applyMeterText(List<View> meters)',
        'private static void syncKeyguardRows()',
        'private static void showIfHidden(Object view)', 'private static void hide(Object view)',
        'private static boolean markCollapsed(View child)')) {
    $method = [regex]::Match($hooks, '(?s)' + [regex]::Escape($signature) + ' \{.*?\n    \}')
    if (!$method.Success) { throw "Missing production method: $signature" }
    $methods += $method.Value.Replace('private static', 'static')
}
$islandReaction = [regex]::Match($hooks, '(?s)private static void reactToIslandChange\(\) \{.*?\n    \}').Value
if (!$islandReaction.Contains('syncKeyguardRows();')) { throw 'Island ownership change must refresh lockscreen suppression' }
$probe = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'RingHandoffTest.java')).Replace('// PRODUCTION_METHODS', ($methods -join "`n"))
$probePath = Join-Path $out 'RingHandoffTest.java'
[IO.File]::WriteAllText($probePath, $probe)
$view = @'
package android.view;
import java.util.*;
public class View {
    public static final int VISIBLE=0, GONE=8;
    int visibility=VISIBLE, width=20, height=20;
    public String slot;
    final List<Runnable> tasks=new ArrayList<>();
    public boolean post(Runnable r) { tasks.add(r); return true; }
    public void flush() { for (Runnable r:new ArrayList<>(tasks)) r.run(); tasks.clear(); }
    public int getVisibility() { return visibility; }
    public void setVisibility(int v) { visibility=v; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void layout(int l,int t,int r,int b) { width=r-l; height=b-t; }
    public void setVisibleState(int s,boolean animate) {}
}
'@
$viewPath = Join-Path $viewDir 'View.java'
[IO.File]::WriteAllText($viewPath, $view)
$sources = @('Prefs.java','ChargeLayout.java','SettingsSchema.java','TrioSettings.java','TrioAppearance.java','TrioGeometry.java','Refl.java','HyperOsIconLease.java') | ForEach-Object { Join-Path $SourceDirectory $_ }
$sources += @('android/content/SharedPreferences.java','android/os/Bundle.java') | ForEach-Object { Join-Path (Join-Path $PSScriptRoot 'shims') $_ }
$sources += @($probePath, $viewPath)
& $javac -nowarn -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE) { throw 'Handoff test compilation failed' }
& $java -cp $classes io.github.fengwenhua.colorduo.RingHandoffTest
if ($LASTEXITCODE) { throw 'Handoff tests failed' }
