# 本地发布：构建 release APK 并直接上传到 GitHub Release。
#
# 用法（在仓库根目录执行，脚本放在 scripts\ 下）：
#   .\scripts\release.ps1 -Version 1.1
#   .\scripts\release.ps1 -Version 1.1 -NotesFile .\work\notes.md
#   .\scripts\release.ps1 -Version 1.1 -DryRun          # 只构建，不打 tag、不建 Release
#
# 版本号 -> versionCode 规则：major*10000 + minor*100 + patch（v1.0 -> 10000）。
# 版本号可写 1、1.0 或 1.0.0，缺的段按 0 计；tag 名与所写的版本号一致。
#
# ★ 这个脚本只推 tag，**不推分支**。所以完整的发布是两步：
#     git push origin main
#     .\scripts\release.ps1 -Version 1.1 -NotesFile .\docs\release-notes\release-notes-1.1.md
# 只跑脚本的话，GitHub 上能下到包、能看到 tag，但默认分支上还没有对应的提交
# —— v1.4 和 v1.6.12 都踩过这个坑（见 docs\DEVELOPMENT.md 与 docs\history\1003-todo.md §31.2）。
#
# 发布成功后会自动清理旧 release：Releases 页面只留刚发布的这一版。
# 只删 release 记录，**tag 一律保留**（历史源码快照仍可用 tag 定位）。
# 想保留历史 Release 就加 -KeepOldReleases。
#
# 默认只发布 ColorDuo 主仓。模块仓申请完成后，可显式传 -ModuleRepo owner/repo，
# 将同一 APK 与说明同步过去；不会写入 HyperDuo 的模块仓。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    # Release 说明（Markdown）。不传时留空，GitHub 会显示为无正文。
    [string]$Notes,

    # 与 -Notes 二选一：从文件读取说明，便于写多行中文。
    [string]$NotesFile,

    # 跳过上传与打 tag，只产出 dist\ColorDuo-<version>.apk。
    [switch]$DryRun,

    # 已存在同名 Release 时覆盖它（默认会报错停下）。
    [switch]$Force,

    # 保留历史 Release（默认只留最新版，删旧的不删 tag）。
    [switch]$KeepOldReleases,

    # 显式指定已申请的 ColorDuo 模块仓；留空只发主仓。
    [string]$ModuleRepo,

    [switch]$SkipModuleRepo
)

$ErrorActionPreference = 'Stop'

$Root    = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tools   = Join-Path $Root '.tools'
$Repo    = 'fengwenhua/ColorDuo'
$Package = 'io.github.fengwenhua.colorduo'

# 原生程序把进度和警告写到 stderr（git 的 "Everything up-to-date"、gradle 的弃用提示）。
# 在 $ErrorActionPreference='Stop' 下，一旦把 stderr 并进管道，这些就变成终止错误，
# 会把一个成功的步骤当成失败中止整个发布。所以在这里临时放宽，只用 exit code 判成败。
function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Exe @Arguments 2>&1
        return [pscustomobject]@{
            Output   = (@($output | ForEach-Object { "$_" }) -join "`n").Trim()
            ExitCode = $LASTEXITCODE
        }
    } finally {
        $ErrorActionPreference = $previous
    }
}

# ---- 版本号校验 -------------------------------------------------------------

if ($Version -notmatch '^\d+(\.\d+){0,2}$') {
    throw "版本号必须是 major / major.minor / major.minor.patch（收到 '$Version'）"
}

# 缺的段按 0 计：1.0 与 1.0.0 是同一个 versionCode，但 versionName 保留所写的形状。
$parts = @($Version.Split('.') | ForEach-Object { [int]$_ })
while ($parts.Count -lt 3) { $parts += 0 }
$code = $parts[0] * 10000 + $parts[1] * 100 + $parts[2]
$tag   = "v$Version"
$asset = "ColorDuo-$Version.apk"
$dist  = Join-Path $Root 'dist'

Write-Host "==> ColorDuo $Version (versionCode $code), tag $tag" -ForegroundColor Cyan

# ---- 构建 -------------------------------------------------------------------

# 发布构建不能用 --offline：R8 需要一个未 vendored 的 compose-group-mapping。
# JDK 21 是硬性要求，不是选择：Miuix 的产物是 21 字节码，而 miuix-nav 的入口
# （rememberNavController / entry）是 inline 函数，Kotlin 不允许把 21 的字节码
# inline 进 17 的目标；AGP 又要求 Java 与 Kotlin 的目标一致。
$env:JAVA_HOME        = Join-Path $Tools 'jdk\jdk-21.0.12.1+1'
$env:GRADLE_USER_HOME = Join-Path $Tools 'gradle-home'
$env:ANDROID_HOME     = Join-Path $Tools 'sdk'

$gradle = Join-Path $Tools 'gradle\gradle-9.8.0\bin\gradle.bat'
# 名字不能叫 $args：那是 PowerShell 的自动变量。
$gradleArgs = @(
    '--project-dir', $Root, ':app:assembleRelease',
    '--no-daemon', '--console=plain',
    "-PcolorduoVersionName=$Version",
    "-PcolorduoVersionCode=$code"
)

$build = Invoke-Native $gradle $gradleArgs
$build.Output -split "`n" | Where-Object { $_ -match '^BUILD |^FAILURE|error:' } | ForEach-Object { Write-Host "    $_" }
if ($build.ExitCode -ne 0) { throw "Gradle 构建失败（exit $($build.ExitCode)）" }

$built = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $built)) { throw "没有找到构建产物：$built" }

New-Item -ItemType Directory -Force -Path $dist | Out-Null
$target = Join-Path $dist $asset
Copy-Item $built $target -Force

# 构建出来的是什么版本，以 APK 自己为准 —— 版本注入写错时这里会立刻暴露。
$aapt = Join-Path $Tools 'sdk\build-tools\37.0.0\aapt2.exe'
$badgingRun = Invoke-Native $aapt @('dump', 'badging', $target)
$badging = [string]($badgingRun.Output -split "`n" | Where-Object { $_ -match '^package:' } | Select-Object -First 1)
if (-not $badging) { throw "无法读取 $target 的包信息" }
Write-Host "    $badging"
if ($badging -notmatch "versionCode='$code'" -or $badging -notmatch "versionName='$Version'") {
    throw "APK 里的版本与预期不符，已中止发布"
}

Write-Host "==> 产物：$target" -ForegroundColor Green

if ($DryRun) {
    Write-Host '==> -DryRun：跳过 tag 与上传' -ForegroundColor Yellow
    return
}

# ---- 凭据 -------------------------------------------------------------------

function Get-GitHubToken {
    $tmp = Join-Path $env:TEMP ("hd_cred_" + [guid]::NewGuid().ToString('N') + '.txt')
    try {
        # git 只从 stdin 读协议行，PowerShell 管道塞不满它的期望，用 cmd 重定向。
        [System.IO.File]::WriteAllText($tmp, "protocol=https`nhost=github.com`n`n")
        $raw = (Invoke-Native 'cmd' @('/c', "git credential fill < `"$tmp`"")).Output
        $line = $raw -split "`n" | Where-Object { $_ -match '^password=' } | Select-Object -First 1
        if (-not $line) {
            throw 'GitHub 凭据不可用：git credential fill 没返回 password。先手动 git push 一次让凭据助手记住。'
        }
        return ($line -replace '^password=', '').Trim()
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

# 发布之后把仓库里的旧 Release 清掉，只留刚发布的这一版。
# 删的只是 Release 记录：对应的 git tag 原样保留，历史源码快照依然能检出。
function Remove-StaleReleases {
    param(
        [string]$ApiBase,
        [hashtable]$Headers,
        [int]$KeepId,
        [string]$KeepTag
    )
    $all = Invoke-RestMethod -Uri "$ApiBase/releases?per_page=100" -Headers $Headers
    $stale = @($all | Where-Object { $_.id -ne $KeepId -and -not $_.draft })
    if ($stale.Count -eq 0) {
        Write-Host '==> 没有需要清理的旧 Release' -ForegroundColor DarkGray
        return
    }
    foreach ($r in $stale) {
        Write-Host "==> 删除旧 Release $($r.tag_name)（tag 保留）" -ForegroundColor Yellow
        Invoke-RestMethod -Method Delete -Uri "$ApiBase/releases/$($r.id)" -Headers $Headers | Out-Null
    }
    Write-Host "==> 已清理 $($stale.Count) 个旧 Release，现在只剩 $KeepTag" -ForegroundColor Green
}

$token = Get-GitHubToken
$headers = @{
    Authorization = "token $token"
    'User-Agent'  = 'ColorDuo-Release'
    Accept        = 'application/vnd.github+json'
}

# ---- tag --------------------------------------------------------------------

Push-Location $Root
try {
    $existing = (Invoke-Native 'git' @('tag', '-l', $tag)).Output
    if ($existing) {
        Write-Host "==> tag $tag 已存在，跳过创建" -ForegroundColor Yellow
    } else {
        $created = Invoke-Native 'git' @('tag', '-a', $tag, '-m', "ColorDuo $Version")
        if ($created.ExitCode -ne 0) { throw "创建 tag $tag 失败：$($created.Output)" }
    }
    $pushed = Invoke-Native 'git' @('push', 'origin', $tag)
    if ($pushed.ExitCode -ne 0) { throw "推送 tag $tag 失败：$($pushed.Output)" }
    Write-Host "    $($pushed.Output)" -ForegroundColor DarkGray
} finally {
    Pop-Location
}

# ---- Release ----------------------------------------------------------------

$api = "https://api.github.com/repos/$Repo"

if (-not $Notes -and $NotesFile) {
    if (-not (Test-Path $NotesFile)) { throw "找不到说明文件：$NotesFile" }
    $Notes = [System.IO.File]::ReadAllText((Resolve-Path $NotesFile))
}

# 只有 404（这个 tag 还没有 Release）才当作「不存在」；网络错误要如实抛出去，
# 否则一次断网会被误判成「这是首发」，接着在创建 Release 时失败得更难懂。
$existingRelease = $null
try {
    $existingRelease = Invoke-RestMethod -Uri "$api/releases/tags/$tag" -Headers $headers
} catch {
    $status = $null
    try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = $null }
    if ($status -ne 404) { throw }
}

$payload = @{
    tag_name   = $tag
    name       = "ColorDuo $Version"
    body       = [string]$Notes
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

if ($existingRelease) {
    if (-not $Force) {
        throw "Release $tag 已存在。要覆盖请加 -Force。"
    }
    Write-Host "==> 覆盖已有 Release $tag" -ForegroundColor Yellow
    $release = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($existingRelease.id)" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
} else {
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
}

# 同名 asset 会被 GitHub 拒绝，先删旧的再传。
foreach ($a in $release.assets) {
    if ($a.name -eq $asset) {
        Write-Host "==> 删除同名旧 asset $asset" -ForegroundColor Yellow
        Invoke-RestMethod -Method Delete -Uri "$api/releases/assets/$($a.id)" -Headers $headers | Out-Null
    }
}

$uploaded = Invoke-RestMethod -Method Post `
    -Uri "https://uploads.github.com/repos/$Repo/releases/$($release.id)/assets?name=$asset" `
    -Headers $headers -ContentType 'application/vnd.android.package-archive' -InFile $target

Write-Host "==> 已上传 $($uploaded.name)  $($uploaded.size) B" -ForegroundColor Green
Write-Host "==> $($uploaded.browser_download_url)" -ForegroundColor Green

# ---- 清理旧 Release ----------------------------------------------------------

# 新版本已经落盘、传完之后才做：清理失败也绝不会影响这次的发布成果。
if ($KeepOldReleases) {
    Write-Host '==> -KeepOldReleases：保留历史 Release' -ForegroundColor Yellow
} else {
    Remove-StaleReleases -ApiBase $api -Headers $headers -KeepId $release.id -KeepTag $tag
}

# ---- Xposed 模块仓 ------------------------------------------------------------

# 指定模块仓时把同一 APK、同一份说明同步过去：模块页的下载链接走模块仓的
# Release，两个仓库版本一旦错开，从模块页进来的用户就会装到旧版。release-module.ps1
# 自己负责校验 badging、上传 asset、同步 module-README.md、清理模块仓旧 Release。
#
# -Force / -KeepOldReleases 原样传递，两个仓库的覆盖与保留语义保持一致。
# 这一步失败不会撤回主仓的 Release；说明为空时模块仓会拒绝发布 —— LSPosed 规范要求
# Release 正文就是 changelog，所以发版必须带 -Notes 或 -NotesFile。
if ($SkipModuleRepo -or [string]::IsNullOrWhiteSpace($ModuleRepo)) {
    Write-Host '==> 只发 ColorDuo 主仓；同步模块仓需显式指定 -ModuleRepo' -ForegroundColor Yellow
} else {
    # A hashtable splat, not an array one: an array splat passes its elements
    # *positionally*, so '-VersionCode' would be bound to the Apk parameter and
    # the call would fail with "找不到接受实际参数". Named keys are required.
    $moduleArgs = @{
        Version     = $Version
        VersionCode = $code
        Apk         = $target
        ModuleRepo  = $ModuleRepo
    }
    if ($Notes)           { $moduleArgs.Notes = $Notes }
    if ($Force)           { $moduleArgs.Force = $true }
    if ($KeepOldReleases) { $moduleArgs.KeepOldReleases = $true }
    try {
        & (Join-Path $PSScriptRoot 'release-module.ps1') @moduleArgs
    } catch {
        throw "Xposed 模块仓发布失败：$($_.Exception.Message)。主仓 Release 已发布成功；直接重跑 release-module.ps1 -Version $Version -VersionCode $code -Apk $target（说明传 -Notes 或 -NotesFile）即可补齐。"
    }
}

Write-Host "==> 设备上已装的旧版本现在可以在「关于 → 检查更新」里看到这个版本。"
