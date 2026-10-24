# ColorOS 16 观察构建

本文记录早期只读观察阶段。当前适配与默认构建说明见 [COLOROS_ADAPTATION.md](COLOROS_ADAPTATION.md)。
下文“只读”约束只针对 ColorOsObserver 与显式指定的 observe 构建。
目标样本：PKH110 / ColorOS V16.1.0 / Android 16 / SystemUI 16.99.12。

## 入口与边界

- 只在 `com.android.systemui` 主进程安装一次。
- 发现 MIUI 电池类时保留原 `TrioHooks`；发现 OPlus 电池类时安装 `ColorOsObserver`。
- 两种类同时存在或都不存在时不安装图标隐藏 Hook。
- ColorOS 只观察原方法参数、原返回值和完成后的 View 状态；原方法执行一次，原异常继续传播。
- 不注册配置广播/网络监听、不订阅目标 Flow、不轮询、不创建 View、不改变 Drawable/显隐/布局/ignored slot。
- View 表使用弱键，值只含简单诊断信息；现场 UI 读取限于主线程，不排队持有 View/Chain。
- 日志限于 128 个最近活动键，重复值去重，每键至少间隔 500ms。快照保留最近采样值。
- 不输出 SSID、电话、通知文本或模型的完整 `toString()`。

## 观察面

电池：bind、attach/detach、configuration、layout、dark/hidden、style/content 更新。
记录 scene、host ID/generation、displayId、尺寸、density、方向、level、tint、style 和父类型。
记录未绑定阶段，不将 `unbound` 误认为已确认的显示场景。

网络：Wi-Fi 模型与每行 slot，普通/BigType/Sprint/TMO 移动 Binder，subId、location、
phoneId、level/numberOfLevels/isInService，以及已注册 ImageView 的类型资源名。
缺少某个子 View 或方法只记录 capability 缺失，绝不全局 Hook ImageView。

当原生电池 `dump(PrintWriter, String[])` 被调用时，追加有界观察快照。
框架日志 tag 为 `HyperDuoColorOS`；入口日志 tag 为 `HyperDuo`，实际可能由 LSPosedFramework 代打。

## 构建与验证

```sh
bash tests/coloros-observer.sh
gradle :app:assembleDebug --no-daemon --console=plain \
  -PcolorOsStage=observe -PhyperduoVersionName=1.1-coloros-observer -PhyperduoVersionCode=20001
gradle :app:assembleRelease --no-daemon --console=plain \
  -PcolorOsStage=observe -PhyperduoVersionName=1.1-coloros-observer -PhyperduoVersionCode=20001
```

使用观察版本标记时，OPPO/OnePlus/realme 设置页的关于卡片显示“观察构建”，不将设置端服务连接
表述为 ColorOS 已适配。该卡片是构建提示，不能代替目标进程加载证据。

2026-10-24 本机原始构建、观察 debug/release（含 R8）构建成功；Java 编译目标保持 21，实际使用 JBR 25.0.3、
Gradle 9.8.0 和 Android SDK 37.0。为适配现有 `compileSdk=37` 增加了 SDK 目录别名
`platforms/android-37 → android-37.0`，没有修改 SDK 平台内容。

新增纯 Java 测试共 15 项：ROM 四种组合、诊断去重/节流、最新状态保留、LRU 有界容量及并发计数。
观察 Java 代码另外通过 Android SDK + libxposed API 102 的直接 javac 编译。
release APK 的 v2 签名验证成功，三个 `META-INF/xposed/` 入口文件均保留。

现有 `RingSlotsTest` 在“empty layout round-trip”断言失败。同一测试在未修改的
`e9dffbcc8f65a5f3dc379c0b25ccdc0462a8a28d` 临时导出中同样失败：`EMPTY_LAYOUT` 为三段短表示，
编码器按扩展后的 `ZONE_COUNT` 输出所有段。此次未改动该编码器或测试，不能报告圆环回归全通过。

## 真机通过条件

安装/启用不等于通过。需要实际日志证明：

1. 入口在 SystemUI 主进程加载，报告 API 102，且选择 COLOR_OS。
2. 核心 battery bind/content Hook 命中；每个实际进入的 scene 能与独立 host 对应。
3. 当前实际 mobile Binder 和 Wi-Fi 行得到数据，隐藏分支仍保持原生。
4. 下拉 QS、内外屏切换与配置变化后，旧 host 不持续被强引用，状态更新不串行。
5. 没有由观察器导致的 SystemUI 崩溃或原生图标缺失。

上述条件和真实绘制/恢复测试是不同阶段。观察通过后再逐项实现组件接管，不把日志里
“installed=true”的 Hook 数量当成适配成功。
