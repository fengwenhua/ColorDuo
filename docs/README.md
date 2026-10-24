# ColorDuo 文档索引

ColorDuo 基于 HyperDuo，保留原作者的架构文档、历史实测和发布说明。历史文件中的
HyperDuo 名称、包名和 HyperOS 测试设备对应当时的上游版本；ColorDuo 当前情况以以下文档为准。

仓库根目录只留两份 README（`README.md` 面向用户、`module-README.md` 是发布到 LSPosed
模块仓的首页），其余文档都在这里。

## 主要文档

| 文件 | 内容 |
| --- | --- |
| [COLOROS_ADAPTATION.md](COLOROS_ADAPTATION.md) | ColorDuo 的 ColorOS 16 接入、构建、已验证功能与当前边界。 |
| [validation/coloros16-2026-10-24.md](validation/coloros16-2026-10-24.md) | Find N5 分阶段真机验证、空占位/箭头/预览修复，以及 fork 改名后的构建验证。 |
| [COLOROS_OBSERVER.md](COLOROS_OBSERVER.md) | 只读诊断构建与配置/网络策略检查。 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发文档。架构、渲染管线、配置键参考、测试方法、发布流水线，以及逐轮的改动记录与踩坑。**多数「为什么这样写」的答案在这里。** |
| [SWITCHES-REVIEW.md](SWITCHES-REVIEW.md) | 开关与设置项的系统性复盘：每一项的门控是否与渲染一致、哪些是死代码、门禁逻辑的边界。 |
| [CAPSULE-INTEGRATION.md](CAPSULE-INTEGRATION.md) | 胶囊样式的接入说明。 |
| [ISSUE-CODE-REVIEW-2026-10-16.md](ISSUE-CODE-REVIEW-2026-10-16.md) | 针对一批 issue 的代码审查记录与修复方案。 |
| [RELEASE-NOTES-STYLE.md](RELEASE-NOTES-STYLE.md) | **发布说明撰写规则**：固定三节结构、每条怎么写、绝对不要写的五种东西、写完自查表。写 `release-notes/` 下任何一份之前先看这个。 |
| [ISSUES-2026-10-22.md](ISSUES-2026-10-22.md) | 上游 2026-10-22 的问题清单，保留供追溯。ColorOS 待验收场景见 COLOROS_ADAPTATION.md。 |
| [history/1003-todo.md](history/1003-todo.md) | **历史交接记录（已归档）**。v1.5 → v1.7.1 的逐轮工作日志，2400 行。待办部分已作废；仍有价值的是开头那张**经验索引**——发布流程坑、PowerShell/编码坑、工装与换环境的约束。 |

## 发版说明

`docs/release-notes/release-notes-<version>.md` 是每次发版时的 changelog 源文件，由
`release.ps1 -NotesFile` 读取，内容即 GitHub Release 正文。

**这些文件必须入库。** 原发版脚本可清理旧 Release，
所以删掉 Release 之后，这里就是旧版本 changelog 的**唯一副本**。

写之前先看 **[RELEASE-NOTES-STYLE.md](RELEASE-NOTES-STYLE.md)** —— 固定的三节结构
（功能添加 / 功能修复 / 功能变更）、每一条该怎么写、哪五类内容绝对不能出现。
以 `release-notes-1.7.2.md` 为范例。

写好后放进该目录，然后：

```powershell
git push origin main
.\scripts\release.ps1 -Version 1.0.0 -NotesFile .\docs\release-notes\release-notes-1.0.0.md
```

## 验证与素材

| 路径 | 内容 |
| --- | --- |
| `validation/` | 各轮改动的验证记录：截图清单、测量数据、逐项核对结果。文件名带日期。 |
| `images/` | README 引用的截图（状态栏效果、设置界面等）。 |
| `ref-5ga.png` | 「5GA 中 A 的比例」这个设置项的参考量测图。 |
| `wifi-presence/` | Wi-Fi 图标显隐的实拍对照。 |
| `wifi-refresh-recovery/` | Wi-Fi 刷新异常的复现与恢复记录。 |

## 相关但不在这里

- `scripts/` — 构建、安装与发版脚本，说明见下。
- `tests/` — 离线验证脚本（真值表、渲染探针），每个都能 `exit 0` 即为通过。
- `work/` — 不入库的工作区，存放一次性的工装与中间产物。
- `.ref/` — 参考实现与反编译源码，不入库。

## scripts/

| 脚本 | 用途 |
| --- | --- |
| `env.ps1` | 设置 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_HOME` 并加一个 `gradle` 函数。**先 source 它**：`. .\scripts\env.ps1` |
| `install.ps1` | 装 debug APK 到设备、打开设置页、跟进日志。`-InstallOnly` / `-LogOnly` 可拆开用。 |
| `release.ps1` | Windows 发版入口：构建 ColorDuo APK、推 tag、建本 fork 的 GitHub Release；默认只发主仓。 |
| `release-module.ps1` | 模块仓申请完成后，显式传 `-ModuleRepo owner/repo` 发布同一 APK；没有上游仓库默认值。 |
| `legacy/build.ps1` | 早期手写的 javac/d8/aapt2 离线管线，**已弃用**——它编译不了 Kotlin/Compose 与 `res/`，保留仅供查阅。 |

活动 PowerShell 脚本从自身位置解析仓库根目录，工具链仍按其 Windows `.tools` 布局查找。
macOS/Linux 使用根 README 的 Gradle 命令；GitHub Actions 构建 artifact 不会向分支提交 APK/base64。

### 发版

```powershell
. .\scripts\env.ps1
git push origin main
.\scripts\release.ps1 -Version 1.0.0 -NotesFile .\docs\release-notes\release-notes-1.0.0.md
```

发版脚本只推 tag，**不推分支**，所以 `git push` 要单独先做；否则 GitHub 上有包和 tag，但默认分支还没有对应提交。
