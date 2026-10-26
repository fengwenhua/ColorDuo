# 优化续作清单

续作基线：`4b71389`（公开 1.1.1 已发布）。按根目录《优化方案.md》逐项核对代码，
将阶段性基础建设和后续未完成部分分开记录；每个实施步骤验证后独立提交。

## 已落地的主要部分

生产签名/更新清单、Actions 发布与两仓验证、正式 Observer 移除、设置内存预览与合并提交、
Lint 门禁、HostDecision、配置 Schema 注册表、首轮 UI/纯辅助类提取、窗口/大字体适配、兼容性报告及工具链校验。
依据分别在 optimization-progress.md 和各项验收记录，不能由此推断所有历史脚本或所有机型均已覆盖。

## 继续顺序与未完成边界

1. **O-06：完整测试迁移（已完成桌面/CI 迁移）**。12 个历史 PowerShell 入口均已转发至 Gradle，保留发版前真机验证层。
   真实渲染/配置、HyperOS 原生图标、网络、时钟和读数均已建立保护；末节记录最后两个入口的收尾。
   旧测试中三分区编码、中部信号行和固定胶囊规则有过期预期；必须记录为什么调整，不能为通过而修改现有功能。
2. **O-08：配置模型分组**。注册表已统一线协议和字段完整性检查，历史读取、copy、equality/hash 仍有直接映射。
   按 Content、Ring/Capsule Layout、Appearance 分组逐步提取，保留读取迁移和两种样式隔离的门禁。
3. **O-09：宿主职责继续拆分**。TrioHooks 仍约 5123 行，首轮只提取 HostTree/Diagnostics。
   先补对应事件序列测试，再提取原生图标 lease、网络采样、时钟/性能读数等；保持 HyperOS 入口与 ColorOS 逻辑。
4. **O-09：渲染快照保护**。现有重复绘制相等检查不是固定 golden。先建立可复核的输出基准及故障对照，
   再决定 Renderer/Geometry 的职责拆分；不改变绘制数学，不把桌面结果当作 Android GPU 验证。
5. **O-10：键盘与焦点检查**。尺寸、描述、RTL、内外屏三档字体及分屏已有验收；箭头的键盘/焦点行为仍需补证。
   未将未做的 TalkBack 语音测试或其他设备样本计为通过。

其他优化项的明确门禁仍逐项核对，例如新增 Lint error 必须失败、发布输入/资产不一致必须停止。
本轮不更换手机签名、不卸载应用；需要真机验证时使用同签名候选包保留配置。

## O-06 迁移进度

| 历史测试组 | 当前 Gradle 覆盖 | 尚存边界 |
| --- | --- | --- |
| glyph-scale | GlyphScaleTest：17 项原像素断言及范围/default 契约 | 旧入口转发 Gradle；单次缩放/限幅以像素故障对照验证 |
| ring-slots | 新增 FreeRingTest：6 个方法、624 项断言 | 旧入口转发 Gradle，重复 Java 已移除；现行预期依据及像素隔离修复见下文 |
| ring-handoff | HyperOsIconLeaseTest 9 组 + RingHandoffTest 1 组桥接 | 原 17 项由当前 8 个 Hook 委托执行；含岛变化源码约束，不代表 HyperOS 固件实机 |
| capsule | CapsuleTest：78 项运行时断言；4 组源集成/宿主尺寸检查 | 修正动态宽度、上下组件预留高度及旧固定文字分界；旧入口转发 Gradle |
| capsule-zones | CapsuleZonesTest：338 项四格编辑/像素/信号断言 | 旧固定时钟预期改为 LEFT 自由组件；正文记录像素归属边界变化 |
| drawing-zones | DrawingZonesTest 15 方法 + DrawingSourceContractTest 4 方法 | 旧三带兼容路径与当前自由组件分别保护；迁移说明见下文，旧入口转发 Gradle |
| glyph-drawable | 新增 host-tests：5 种形态保留 156 项断言，另加写槽失败回退 | Hook 方法体来自当前源码的隔离生成；不代替 Xposed/OEM/GPU 验证 |
| wifi-presence / battery-presence | host-tests：18 组 Wi-Fi 组合+3 次转换、44 项电池断言及温度过滤 | 当前实际方法体生成；不能代替真实 Android 服务/广播 |
| perf-layout | 独立 readoutTest：76 项原断言、4 组集成约束，旧入口转发 | Paint/View 替身与主宿主测试隔离；不代表 Android 字体/OEM 测量 |
| os4-layout | 独立 stripTest：2776 项布局/恢复及 144 项全排序断言 | 实际 14 个 Hook 方法+生产 OutRingLayout/Refl；不代表 HyperOS 固件实机 |
| issue-regressions | 独立 issueTest：12 组/663 项运行时断言 + 8 组源契约 | 12 个 PS 入口已统一；不代表 Android 服务/Compose 手势实测 |

GlyphScaleTest 的验证：`:render-tests:test --tests io.github.fengwenhua.colorduo.GlyphScaleTest` 成功，
JUnit 输出 17 项断言。仅在 `.local` 临时源码中注入三种故障，忽略缩放、取消限幅、重复缩放均触发像素/边界断言失败；
生产源码未改动。测试直接编译真实生产 Renderer/Appearance/Geometry，替身只在桌面测试项目中存在。

### ring-slots 迁移说明

新 FreeRingTest 保留空模型、编解码/迁移/传输、编辑、候选优先级、共享外观所有权和像素六组契约。
按当前代码与已有 UI 规则修正以下历史预期，未修改生产源码：

- 布局的规范输出是四格 `|||`，短输入 `||` 继续兼容；五格输入的多余部分忽略。
- 旧聚合 MOBILE（2）迁为 SIM 1（7），同格去重保留优先级。
- 圆环只绘制上、中、下三带；左格是胶囊专用，不计入圆环像素帧。
- 信号行不能放圆环中部：增加拒绝放置/菜单排除检查，保留合法区域的绘制检查。
  已退役聚合 MOBILE 仍验证旧布局/直接模型读取，菜单不再提供该项。
- 组件像素参照改为真实 `drawBattery` 的同一帧背景。旧方法清空 parts 会关闭动态底部开口，
  从而把电池弧变化误当作组件越界；新参照保持所有缺口参数一致。

六组共 624 项断言，其中 126 个合法区域/组件/描边/字号组合各检查 occupant、非空像素、区域边界及电池弧重叠。
完整 `:render-tests:test` 共 19 个方法全部通过。临时副本中将新增项改为追加、取消 MOBILE→SIM 1 迁移、
将组件垂直偏移 12 单位，三种故障均被测试拒绝；没有把故障代码写入生产源文件。

### 原生图标 lease 边界提取

为迁移交接测试，将 TrioHooks 中的 9 个实现提到 HyperOsIconLease，保留原委托方法及 Hook 调用点。
生产弱引用 map 仍由同一实例持有，KEYGUARD_ROWS/COLLAPSED 兼容入口与新类引用同一集合；
meter 回调执行时才读取配置和解析后的字段，避免把排队前状态固定下来。
TrioHooks 由 5123 行降至 4983 行，未变更 ColorOS 接管、绘制数学、网络采样、时钟或布局时机。

直接 JUnit 验证 9 组事件，包括原 17 项交接契约及多个 manager、stacked_mobile 别名、字段延后解析、
原生 setter/post 失败与一次性 acquire/release。
另将修改前/修改后的真实方法分别交给更新后的历史 Java fixture，均输出 `PASS: 17 production handoff assertions`。
旧脚本补新类编译依赖；旧 fixture 的聚合信号预期按现行 SIM 1 设置表达，旧 id 的迁移另由 FreeRingTest 验证。

完整检查：28 个 app 方法、28 个 render 方法、Lint、release/debug 构建全部通过；正常 release 无 Observer。
未安装到手机，没有更换签名、改配置或重载 SystemUI。尚无 HyperOS 第二台设备，桌面队列检查不计为固件实机验收。
接下来的职责仍包括网络采样、时钟/性能读数和更完整的历史渲染工装迁移。

### Drawable 宿主测试迁移

新增独立 host-tests 项目，测试替身与 render-tests 隔离。生产 GlyphDrawable/Refl 原样编译；
绘制 Hook 的当前方法体由唯一标记/明确边界生成进测试夹具，保留原调用顺序验证。
五种原宿主形态分别通过 34/34/31/31/26 项断言；新增字段类型不兼容的写入失败回退。

同时将旧 PS 的原 Java 测试体逐形态独立编译运行，五个输出计数与新 JUnit 一致。
临时源码中删除“仅释放自身 Drawable”的 guard，或将卸载移到原生 draw 之后，均被新的测试拒绝。
所有 app/render/host 测试、Lint、release 构建通过；普通与正式发布 CI 都加入 host-tests。
实际测试数为 28/28/6，未运行 PowerShell 引擎，未操作手机。

### Presence 与旧启动器统一

host-tests 继续读取当前真实 resolve/publish/功率/文本方法，保留 18 组 Wi-Fi 组合、3 次重连转换，
以及原 44 项功率/温度/NaN 去重断言，另补温度“仅充电显示”过滤。
旧 Java 夹具在本机独立编译运行，输出计数与 JUnit 相同；旧 battery fixture 缺少现行的温度过滤字段，
补默认 false 后保持原运行时断言不变。两项源集成约束保留 Wi-Fi 特权 UID 接收/关机跳过查询及 TextView 两行设置。

四个原 PS 入口改为 tests/gradle-test.ps1 的兼容启动器，移除重复的 Java 字符串与编译逻辑。
Drawable 的 SourceDirectory 参数映射为 hostTestProductionSources，使用仅有三个原源文件的目录实跑通过。
原 GlyphDrawable 三项源码约束进入 JUnit；GlyphScale 的字面实现约束以 17 项像素断言、范围/default 契约及三个故障对照覆盖。
原生成断言未因删除旧生成器而丢失，未将已淘汰的实现拼写当作新的产品约束。

临时取消 NaN-safe 去重、或忽略实时 Wi-Fi 状态后，测试分别因重复刷新/旧缓存覆盖实时状态失败。
完整 app/render/host、Lint、release 门禁成功，当前方法/实例数为 28/29/14。
PowerShell 文件保持 CRLF；未执行 pwsh，也未操作手机。

### HyperOS 读数纯边界

提取 HyperOsReadout：电流/电压换算、两行文本、温度充电过滤、功率舍入和行数。
原 wattsOf/perfText/perfLines/powerText 委托入口保留，PERF_BREAK 保留原字符；
perfText 调用前仍检查 appearance 可用性，格式化只接收当前标量，不持有 Context、View 或全局状态。
未修改 ColorOS 的环外横排读数和真实功率采样。

旧实现与新纯类对比 640 组固定输入（四种开关/接电维度、八种功率、五种温度），文本及行数均一致。
现有 44 项电池读数断言、温度过滤新增检查和全部门禁均通过；app 28、render 29、host 14，
Lint、debug/release 构建成功。perf-layout 旧入口补充了新类编译依赖。
TrioHooks 当前为 4937 行；仍需进一步处理读数 View/尺寸、时钟与网络采样职责。

### 读数布局工装迁移

新增 host-tests 的 readoutTest 源集及 Test 任务，与 Drawable/Presence 的替身和类路径隔离，
host-tests:test 自动依赖它。42 项当前方法/常量绑定以 TSV 声明，要求生产来源唯一匹配。
实际读取原 perfTextSize/perfLineSpacing/perfWidth/行高、字体与 inkScale 方法体，未复制另一套尺寸算法。
TextView 替身名称对齐 android.widget；绘制参数与原 Java 工装相同，原 76 项断言保持通过。
原字重/字体重同步、共享 strip 宽度与释放、行距写入等约束分为 4 个 JUnit 方法。

旧 Java 工装独立编译/执行先通过 76 项，新 Gradle 输出计数相同。
临时忽略读数尺寸、按当前数字测量预留宽度、将多行宽度求和三种故障均被拒绝。
perf-layout.ps1 改为统一启动器，底层两项 --tests 参数和全部项目门禁实跑通过。
当前为 app 28、render 29、host 19（普通14+隔离读数5），Lint/release 成功；没有操作手机或宣称 Android 字体实测。

### 胶囊轮廓与文字像素迁移

capsule.ps1 原 Java 夹具引用已改为函数的 CAP_INK_W/CAP_DOT_CX，原样编译失败；
其旧宿主只增宽、不增高，也不再符合上下开口组件已预留高度的现行规则。
迁移到 CapsuleTest 后读取 capInkW()/capDotCx()，宽宿主同时采用 capHostHeightPx，
轮廓保持原状态栏高度，上下 band 使用新增空间。仍保留 78 项轮廓增长/方向、边距、
双宿主比例、信号开口、旧几何辅助契约、字号/字重及 Bundle 回退断言。

现行左右读数各自靠边绘制，不再按旧文字宽度比例的固定分界裁切。
旧半图比较会把跨过旧分界的时钟墨迹误报为电量变化；新夹具将实际 drawText 调用按文字分别绘制到独立像素层，
比较完整文字像素，要求两帧均实际绘制该文字。时钟的尺寸可达性按现行整条阅读轨道检查。
没有修改生产绘制代码，也没有把缺失的文字当成“没有变化”。

另 4 个 JUnit 方法保留原源码集成约束，并检查两档长度/三档宿主高度的 body scale 与 band 空间。
修正读取样式时的正常空白差异；其余旧测量、View setter、单次整体缩放、点行中心线、共享文字 fitter 和 UI ID 映射约束保留。
临时反转电量填充、把时钟字号/字重各自绑到电量设置，三种故障均触发断言失败。
独立 Java main 和 Gradle 均输出 78 项通过；不宣称原过期脚本原样通过。
capsule.ps1 成为第六个统一 Gradle 入口；未执行 PowerShell 引擎，未操作手机。

### 胶囊四格编辑工装迁移

CapsuleZonesTest 保留像素归属/可编辑性、编辑后场景稳定、组件放置及信号数据四类检查，扩至 3 个 JUnit 方法、338 项断言。
旧脚本因 CAP_DOT_CX 常量移除而无法原样编译；改为 capDotCx()，并按当前 UI 规则更新夹具：

- 时钟属于独立 LEFT 格，四格均可清空或放置文字/图标，使用实际编辑器的 withPiece/placementChoices。
  旧“TOP 是不可移除固定时钟”“右格不接受时钟”的预期已过期；不改变旧 assign 兼容入口来迁就测试。
- 初始显式四格 `7|3|8|6`，逐格清空并核对 4 种 Wi-Fi/充电组合以及其他三格，防止清空被旧外部左格配置覆盖。
  全部从未放置的空布局继续有历史默认回退，不把它当成某个格子的清空结果。
- 在原描边/字号/电量极值组合上增加两档胶囊长度，共 32 帧。全部墨迹必须有可编辑区域，
  时钟/电量差分必须留在 body，左右文字锚点必须各自命中 LEFT/CENTRE。
  当前读数可在整条轨道上从边缘增长，所以不再以旧固定文字分界裁切墨迹来判断部件归属；
  独立设置是否相互影响由上一组实际文字像素层检查保护，没有降低到“画了东西就算通过”。
- 原 4 项移动强度/电量无关/双卡差异像素检查全部保留。

在临时副本中将右侧命中区误放到左侧、用电量驱动信号、忽略清空，三种故障均失败。
全部 app/render/host、Lint/release 通过，计数为 28/37/19。旧 capsule-zones.ps1 转发统一 Gradle 入口，
没有运行 PowerShell 引擎，没有更改生产绘制或编辑代码，没有操作手机。

### HyperOS 4 环外布局工装迁移

新增 stripTest 源集和隔离 JVM，普通 host-tests:test 自动依赖；保留原 ViewGroup、弱引用预留、
post 队列及可见性/几何模型。21 项绑定读取当前 14 个 Hook 方法和实际 Prefs 常量/优先级/索引函数，
实际 OutRingLayout/Refl 直接编译，不把固定替身 ID 冒充生产配置。

原 Java 工装在当前代码中执行失败，因为 bounded 把 alpha=0 的已牺牲组件也与邻居检查重叠。
新夹具只排除这种不可见对，其余按实际视觉坐标、等比缩放、岛边界、padding 恢复及 coalesced post 检查；
调整后独立 Java 输出 2776 项，Gradle 输出一致。保留当前“一轮牺牲一个、再比例缩放”的 solver 行为，
未改为另一种挤压策略，也没有删除 ColorOS 为此保留的多轮重算。
另补六种排列 × 两个方向，共 144 项实际 View 映射/牺牲优先级/位置及恢复检查。

仅在临时源文件中忽略岛边界、取消预留 post 去重、反转牺牲优先级，均触发断言失败。
最小四文件替代源码目录实跑成功，旧 -SourceDirectory 映射到独立 stripTestProductionSources，
不干扰 Drawable 的三文件替代源测试。os4-layout.ps1 成为第八个统一启动器，未运行 PowerShell 引擎。
完整门禁 app/render/host 28/37/21、Lint/release 均通过；没有操作手机，没有把桌面模型计为 HyperOS 4 实机验收。

### 本轮收尾：圆环预览底边点击范围

drawing-zones 原 Java 夹具能编译，但在最大描边的闭合圆环中发现实际遗漏：
底部墨迹约到 y=120.987，而命中区只到旧 SVG BOX=120。新增 RingHitBoundaryTest 的两项检查先复现失败：
96 像素宿主的 (38,95) 有墨迹却无命中区域，实际 ink 底边也无法命中 BOTTOM。

只将圆环 BOTTOM 的外边界扩到 max(BOX, INK_Y+INK_H)，保留内部切线和其余样式规则。
18 个极值描边/布局/宿主比例帧逐像素检查均通过，ink 外仍拒绝点击；
修复前后 18 帧的像素摘要相同，未改变绘制输出。

按维护者最新要求，本轮完成此修复、提交并通过 CI 后发布 1.1.2，不继续展开后续优化。
drawing-zones 全组、issue-regressions、配置模型分组、剩余宿主职责提取、golden 与键盘/焦点验收仍留在本清单，
不将两项新增边界测试视为整组历史工装迁移完成。

### 绘制区域工装迁移（O-06 当前步骤）

15 个运行时方法覆盖 22 种配置、3 档电量和两种 Wi-Fi 状态的 132 帧矩阵，
另保留变换/预览 inset、三位数列宽、可绘制组件、兼容 assign 最少修改/拒绝穷举/清空及点大小检查。
四个源契约方法保留共享变换、预览委托、逆变换和矩形切线的原集成约束。

旧原样夹具在当前自由布局规则下不能通过。新夹具显式启用 SIM、关闭类型的 Wi-Fi 互斥过滤，
用实际布局决定闪电的放置/移除，排除胶囊专用 LEFT，动态缺口比较同时覆盖上下开口。
组件归属由逐像素差分检查；电池弧重排时仅断言绘制改变，保留历史 Wi-Fi 相邻带容许范围，
更严格的现行自由圆环组件边界另由 FreeRingTest 保护。未改变生产代码以迁就旧夹具。

矩形为已退役的兼容渲染路径：桌面字体在最大字号时略过固定上切线，
仅在中心列和切线前 1 设计单位内允许该差异，仍保留三位数横向严格边界。
JUnit 每例恢复原独立 JVM 的 CENTER 画笔初始状态，避免先运行的胶囊测试残留左右对齐；
不逐帧重置，不将此项算作现行样式切换验证。
临时副本将矩形读数上移 4 单位、允许零级 Wi-Fi、反转兼容三带逆变换，三项均被新测试拒绝。

完整 app/render/host 为 33/59/21，Lint 通过；drawing-zones.ps1 成为第九个统一入口。
没有运行 PowerShell 引擎，没有操作手机。O-06 仍有 issue-regressions 等历史入口未完成迁移。
按用户最新要求，提交此项后优先修复开启充电功率、插电时圆环消失的问题。

Linux CI 首轮在旧矩形三位数检查失败。本地对照进一步发现桌面 Paint 使用整数字符宽度：
48.3 字号下文本宽 93，字号缩放为 0.9 后宽 81，与期望 83.7 不符，破坏生产 fitter 依赖的浮点比例。
仅修正 tests/shims 的 Paint/Canvas，测量与逐字绘制统一使用浮点 advance，开启 fractional metrics。
新增 TextMetricsTest 覆盖普通/粗体、三种 tracking/文本/缩放，旧 Paint 副本会失败。
保留完整像素边界检查，不通过放宽水平切线掩盖该问题；生产 APK 源码未改动。
Gradle 开启完整失败详情，云端后续若失败可直接定位断言。

### 充电功率优先修复

O-06 当前绘制区域迁移步骤通过 CI 后，按用户要求优先处理开启充电功率、插电时圆环消失。
修复尺寸待布局和实际空间不足两种分支，不再因可选环外读数将宿主永久标记失败。
本地门禁为 app/render/host 35/59/21，1.1.5 同签名候选 Actions build/sign 成功，未公开发布。
Find N5 圆环拔插、胶囊拔插和折叠已由用户确认，日志与截图验证通过。
详情见 [充电读数验收](charging-readout-resize-2026-10-26.md)。

### 历史 issue 回归迁移

新增 host-tests:issueTest，普通 host-tests:test 自动依赖；信号/着色/时钟的 Android 替身与另三套隔离。
原 12 个 suite 先独立 Java 编译运行：11 组共 531 项通过，nudges 在编辑临时字段 editingChargeScene 的反射相等性断言失败。
按生产类明确的“不持久化、不参与相等性”契约排除此游标，并增加相等/hash、复制、Bundle 三项路由元数据检查。
修正后 nudges 为 132 项，全部运行时合计 663；改造后的 Java main 与 JUnit 每组计数一致。

7 个真实 TrioState/TrioHooks 方法按唯一标记生成，TrioTint/TrioZones/OutRingLayout 和配置类直接编译。
JUnit 每例清理夹具静态状态，保持旧每组独立 JVM 的初始条件，不清理被测回调内部的状态变化。
原源契约分成 8 个 JUnit 方法；其中预览状态、长按和反馈检查跟随既有 DrawingPreview/DrawingZones 文件提取，
仍绑定真实绘制输入、当前 lambda、布局写入和反馈。原分界表达式减自身的断言改成真实左右命中边界，未改生产算法。

临时副本取消限频、别名持有 Rect、错误缓存失败时钟隐藏、移动胶囊命中边界、颠倒高亮两遍顺序，五类故障均被拒绝。
五文件最小替代源码目录、Suite 对应的底层 Gradle 方法筛选实跑；issue-regressions.ps1 成为第十个统一入口，保留参数与 CRLF。
未执行 PowerShell 引擎，未操作手机或发布新版本。当前 app/render/host 为 35/59/41，共 135 个 JUnit 方法/实例。
ring-slots 与 ring-handoff 的当前 Gradle 覆盖已存在，两个旧启动器仍待最后统一；O-06 不据此宣告全部收尾。

### O-06 最后两个入口统一

ring-slots.ps1 改为运行既有 FreeRingTest，6 组/624 项断言不变。
ring-handoff.ps1 同时运行 9 组真实 lease 测试和新增的原始桥接序列：8 个委托方法取自当前 TrioHooks，
原 17 项断言逐项保留。生成前仍要求 reactToIslandChange 调用 syncKeyguardRows，方法来源必须唯一。
旧脚本直接 javac 的清单遗漏当前 TrioAppearance 引用的 TrioZones，因此原命令不能独立编译；
补齐当前依赖后旧 Java 序列输出 17 项，新 Gradle 输出相同，没有改生产类来迁就工装。

两个 -SourceDirectory 参数映射到独立 ringTestProductionSources / leaseTestProductionSources，
分别以 8 文件、9 文件的最小目录实跑；未要求复制整个源码树，也不影响其他宿主替代源。
临时目录反转插入优先级、删除真实 hideIcon 委托、删除灵动岛锁屏同步，三种故障均被拒绝，证明参数实际影响被测来源。
重复的 tests/RingSlotsTest.java 删除，RingHandoffTest 移到受 Gradle 生成管理的模板；Git 历史保留旧工装。

本地 app/render/host 为 35/60/41（136 个 JUnit 方法/实例），Lint/release 通过。
12 个历史回归入口已统一，O-06 的纯逻辑/桌面测试和两级验证入口收尾；旧脚本引擎本身仍未在本机执行。
真机验证仍由 verify-device.py 和既有签名候选流程承担，桌面替身不替代 OEM/Canvas/手势验收。
本步骤没有改动手机或生产代码，下一步按计划进入 O-08 配置模型分组。

### O-02 发布恢复补全

O-06 收尾提交 `3ee4ae2` 的 CI 40335174693 通过后，按 1.1.5 发布中实际观察到的恢复缺口补强 O-02。
GitHub 按 tag 查询只找到公开版本；上传超时留下的草稿需从有权限的分页列表定位。
发布器现在复用唯一草稿及已完成附件，不因客户端丢失响应重复创建或上传。
已有完整公开版本只读验证；重复草稿、元信息不一致、重复/额外附件和未完成上传均停止且不删除现场。

新增七组 Python 行为测试，模拟远端已完成上传但响应超时、分页、重复草稿、说明冲突、公开版本重跑、
额外/重复附件、starter 未完成。旧实现上六项失败、一项报错，修复后发布组 13 项、全体 Python 21 项通过。
本步骤没有创建或修改真实 Release，没有操作手机；完成后继续 O-08 配置模型分组。

只读核对现有 1.1.5 时，主仓说明为 LF，模块仓网页说明含 18 个 CRLF；正文统一换行后相同。
发布器仅统一 CRLF/LF，不忽略实际文字；新增已公开模块仓只读重跑测试。
发布组为 14 项，全部 Python 22 项通过；未改动线上说明或附件。

### O-08 第一组：胶囊几何

前项 726202a 的 CI 40336746920 通过后，提取不可变 CapsuleLayoutSettings；五项读数的默认和限幅共用一个读取入口。
内部快照访问调整，Schema/偏好/广播仍保持原五个扁平 key 和 VERSION=1。
70 组线值及 140 帧像素摘要在提取前后相同，663 项历史 issue 断言保留；新增三个快照/局部更新/默认回归。
三种临时模型故障与可选源码覆盖故障均检出，原最小替代目录继续可用。
当前 app/render/host 为 35/63/41，Lint、debug/release 通过，没有操作手机。
后续仍有 Content、环布局、Appearance 分组及最终新增设置单声明验收，详见 capsule-settings-group-2026-10-26.md。

### O-08 第二组：组件内容

胶囊几何提交 9065c93 的 CI 40342641447 通过后，提取不可变 ContentSettings，
覆盖 11 项样式组件设置与 5 项全局标志。偏好/Bundle 共用迁移读取，copy/equality 使用整组，
单 key 更新和跨样式组件复制保持各自作用范围。ColorOS 绘制、充电解码和布局求解候选批量构造内容组。
61 项逻辑字段与 Schema VERSION=1 保持不变，两种系统的已解析 TrioAppearance 接口不变。

84 组规范线值及 336 帧像素与修改前相同；六个新 JUnit 方法和三种故障对照覆盖历史回退、
局部更新及全局标志隔离。配置组登记也与 record 成员逐一核对，旧 issue 的 663 项断言继续执行。
环布局、Appearance 和最终新增设置单声明验收仍待继续，未将当前步骤计为 O-08 全部完成。
详见 content-settings-group-2026-10-26.md。
