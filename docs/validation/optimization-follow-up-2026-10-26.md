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
| glyph-scale | 新增 GlyphScaleTest，17 项原像素断言，main 方法体保持一致 | 原 PS 源码结构检查保留，尚未删除旧脚本 |
| ring-slots | 待迁移 | 旧三分区/全部组件可放中部等预期需要与当前规则核对 |
| ring-handoff | 待迁移 | 旧脚本用源码片段和特殊 View 替身；不能用模型替身冒充真实 lease |
| capsule / capsule-zones / drawing-zones | 待迁移 | 固定胶囊历史规则与当前自由布局有差异，需要区分历史与现行断言 |
| glyph-drawable | 待迁移 | 三种宿主形态与恢复时序需保留 |
| wifi-presence / battery-presence | 待迁移 | 源码注入工装需迁到生产纯逻辑边界或合适的隔离测试项目 |
| os4-layout / perf-layout / issue-regressions | 待迁移 | 多套 Android 替身有冲突，需要隔离而非修改生产代码迁就替身 |

GlyphScaleTest 的验证：`:render-tests:test --tests io.github.fengwenhua.colorduo.GlyphScaleTest` 成功，
JUnit 输出 17 项断言。仅在 `.local` 临时源码中注入三种故障，忽略缩放、取消限幅、重复缩放均触发像素/边界断言失败；
生产源码未改动。测试直接编译真实生产 Renderer/Appearance/Geometry，替身只在桌面测试项目中存在。
