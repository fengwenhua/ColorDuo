# HyperDuo → ColorDuo 迁移核对与充电问题分析

本轮按用户要求暂停继续发布，只分析源码、读取设备状态并进行本地验证。
未修改应用行为、手机配置或既有 Release；模块仓的 Release 尚未发布。

## 核对范围与结论

- 来源：`/Users/panghoo/code/lsposed/HyperDuo`，HEAD `cd80bd7`。
- 目标：`/Users/panghoo/code/lsposed/ColorDuo`，分析基线 `c09a606`。
- 原适配提交 `cd80bd7` 是 ColorDuo HEAD 的祖先，迁移保留了完整提交。
- 来源仓184个受 Git 管理文件均有目标对应文件，缺失0；来源没有额外未跟踪且未忽略的文件。
- 原适配提交修改的48个文件全部有对应文件，其中40个属于 `app/src`。
- `app/src` 共73个文件。统一包名、类名、品牌和换行，并忽略保留的版权声明差别后，69个一致。
- 58个 Java/Kotlin 文件中，56个逻辑一致；另外2个的差异如下。

| 文件 | 迁移后的额外变化 | 是否涉及本次充电问题 |
| --- | --- | --- |
| `ui/SettingsScreen.kt` | 激活卡片按构建阶段识别观察包；正式 outside 阶段显示正常激活卡片 | 否 |
| `ui/UpdateController.kt` | 更新仓库改为 `fengwenhua/ColorDuo` | 否 |

另外2个应用资源差异是中英文模块描述和关于文案。其余文件差异为版本/独立包名、构建与发布流程、
脚本路径和文档更新。Manifest、Xposed 入口、作用域、R8 保留规则、配置广播与发送者校验均随新身份迁移。
默认适配阶段仍为 `outside`，未退回早期 battery/ring 观察阶段。

网络类型让位、原始读数与接管计划分离、空移动组占位、上下行箭头隐藏、快捷编辑缓存更新、
字体、折叠/时钟和环外组件相关代码均保留，没有发现源码迁移遗漏。

逐文件散列和规范化差异另存于本地忽略目录 `.local/migration-audit/`，不包含手机原始日志。

## 问题一：圆环右侧的充电闪电

设备的 SystemUI 视图树确认 `battery_charge_icon` 可见，多个宿主中分配了36px宽度；
`battery_percentage_view` 不可见。原生状态为 `charging=true`、`quick=false`。
因此右侧图标来自系统的原生充电子视图，而非 ColorDuo 环外组件绘制。

两个目录的 `ColorOsBatteryAdapter` 和 `ColorOsRingPreset` 逻辑相同：

1. 读取原生 `battery_charge_icon` 的可见性，作为 `outsideCharge`。
2. 原生图标可见时，设置绘制快照 `paint.showBolt = false`，没有隐藏该原生子视图。
3. 当前 `TrioAppearance` 的闪电规则已改为组件排序：构造时 `boltWanted=true`，
   `hasInk(PART_BOLT)` 按充电状态决定；不再读取 `showBolt`。

所以这里存在旧适配规则与当前渲染规则不一致的问题：`showBolt=false` 不能防止排序中的闪电被画出。
若排序包含闪电，原生和模块闪电可能同时出现；若排序不含闪电，仍保留系统右侧闪电。

读取时设备圆环配置为 `5,1|3|7|`，即「上部网络类型/Wi-Fi、中部电量、下部SIM1信号」，没有闪电组件。
此配置下，右侧系统闪电就是现有适配逻辑的直接结果。不能仅通过迁移 `showBolt` 布尔值解决。

修复应统一原生与模块闪电的显示权：明确哪些充电帧由模块表示、哪些需要原生回退，
并覆盖关闭模块、绘制失败和配置/折叠变化的恢复路径，避免只强制隐藏系统子视图。

## 问题二：普通充电与快充的编辑相互影响

两个目录当前源码都只有按圆环/胶囊区分的布局存储：`ring_layout` 与 `ring_layout@capsule`。
没有普通充电或快充专用的布局键。

- `DrawingScreen.PreviewScene` 的普通充电与快充都设置 `charging=true`，快充额外设置 `quickCharging=true`。
- 预览场景是合成读数；排序编辑仍调用同一个 `applyRingAssignment()`，没有把场景作为存储维度。
- `TrioAppearance.ring()` 的布局解析接收 `charging`，没有 `quickCharging` 参数。
- `TrioRenderer` 在画闪电时使用快充标志选择颜色，并不据此切换中部布局。
- `charging_shows_value` 也是一个共同开关，在 `charging=true` 时覆盖圆环中部，未区分普通充电与快充。

因此，当前实现无法分别保存「普通充电中部数字、快充中部闪电」。这种限制在来源与目标源码中相同，
并非迁移时遗漏独立布局代码。用户记忆中的旧表现不能仅凭当前源码还原。

若要满足该需求，必须让普通充电与快充的中部规则能分别表达，并贯穿设置存储、复制/比较、
配置序列化、预览、真实绘制和原生图标交接，不能仅改变预览界面的局部状态。

## 本地验证与数据迁移边界

- 现有纯 Java 检查重新运行通过：observer15、revision gate31、network warning22、type labels14，共82项。
  这些检查不覆盖上述两个充电问题。
- 分别编译来源和目标的真实 Prefs/Settings/Appearance/Geometry/Zones，使用桌面 SharedPreferences/Bundle
  替身运行定向复现。未调用的触摸坐标变换使用会抛错的替身；本项不是 Android 像素或原生视图测试。
- 两边结果一致：中部布局含闪电时，即使 `showBolt=false`，普通充电仍解析为组件4（闪电）；
  开启 `chargingShowsValue` 后解析为组件3（电量）；设备当前中部布局解析为电量。
- 本轮没有安装新 APK、切换手机设置、重载 SystemUI 或重跑175项 Android 探针。

源码迁移和用户配置迁移是两件事。新包名、新偏好文件和新 LSPosed 模块身份不会自动继承旧应用数据。
本轮查询时设备只安装 ColorDuo，旧 HyperDuo 包和旧应用偏好目录已不存在，无法读取旧偏好进行逐项比较。
因此结论是「未发现源码遗漏，充电规则存在已继承的问题，旧配置是否一致无法复核」，不能写成所有运行行为都已完整验收。
