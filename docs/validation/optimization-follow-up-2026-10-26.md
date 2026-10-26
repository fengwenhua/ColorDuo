# 优化续作清单

续作基线：`4b71389`（公开 1.1.1 已发布）。按根目录《优化方案.md》逐项核对代码，
将阶段性基础建设和后续未完成部分分开记录；每个实施步骤验证后独立提交。

## 已落地的主要部分

生产签名/更新清单、Actions 发布与两仓验证、正式 Observer 移除、设置内存预览与合并提交、
Lint 门禁、HostDecision、配置 Schema 注册表、首轮 UI/纯辅助类提取、窗口/大字体适配、兼容性报告及工具链校验。
依据分别在 optimization-progress.md 和各项验收记录，不能由此推断所有历史脚本或所有机型均已覆盖。

## 继续顺序与未完成边界

1. **O-06：完整测试迁移**。现有 Gradle 门禁不等于 12 个历史 PowerShell 工装均已迁移。
   先迁移真实渲染/配置测试，再为 HyperOS 原生图标、网络状态、时钟和读数提取建立保护。
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
| ring-slots | 新增 FreeRingTest：6 个方法、624 项断言 | 原脚本保留；更新过期预期的依据及像素隔离修复见下文 |
| ring-handoff | 新增 HyperOsIconLeaseTest：9 个方法直接执行生产类 | 17 项历史断言提取前后相同；View/OEM 模型不代表 HyperOS 固件实机 |
| capsule / capsule-zones / drawing-zones | 待迁移 | 固定胶囊历史规则与当前自由布局有差异，需要区分历史与现行断言 |
| glyph-drawable | 新增 host-tests：5 种形态保留 156 项断言，另加写槽失败回退 | Hook 方法体来自当前源码的隔离生成；不代替 Xposed/OEM/GPU 验证 |
| wifi-presence / battery-presence | host-tests：18 组 Wi-Fi 组合+3 次转换、44 项电池断言及温度过滤 | 当前实际方法体生成；不能代替真实 Android 服务/广播 |
| perf-layout | 独立 readoutTest：76 项原断言、4 组集成约束，旧入口转发 | Paint/View 替身与主宿主测试隔离；不代表 Android 字体/OEM 测量 |
| os4-layout / issue-regressions | 待迁移 | 多套 Android 替身有冲突，需要隔离而非修改生产代码迁就替身 |

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
