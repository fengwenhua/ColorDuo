# 当前架构

入口 `ColorDuoModule` 按 ROM 能力选择 ColorOS 适配器或保留的 HyperOS Hooks。
ColorOS Observer 只在 debug/显式 observe 构建安装；正式包由 R8 去除观察代码。

## 设置界面

- `SettingsScreen`：页面壳、服务状态连接、导航内容和公共对话框。
- `SettingsNavigation`：目的地、底栏外观与导航动画。
- `AboutSettings`：关于、模块信息、更新与安装器入口。
- `ActivationStatus`：LSPosed 激活、作用域和功能计数。
- `AppearanceSettings`：颜色、滑块及共享外观控件。
- `DrawingScreen`：编辑卡片和组件页面的编排。
- `DrawingPreview`：预览场景、示例绘制及可滚动场景选择器。
- `DrawingZones`：区域选择、移动反馈、候选组件优先级。
- `DrawingOutsideParts`：环外布局与排序。

本轮提取前后核对 101 个顶层声明的函数体相同，只调整文件位置、import 与跨文件 internal 可见性；
编译、配置一致性和绘制检查负责验证引用及输出。保留原作者版权与 GPL 说明。

## 系统界面

ColorOS 主线程负责采样与执行，`HostDecision` 作纯决策；网络、电池尺寸、充电图标和时钟分别持有 leases。
只有实际已绘制的当前帧可以提交接管。细节见 [宿主状态](HOST-STATE.md)。

HyperOS 继续沿用 `TrioHooks`、`TrioState` 和既有原生图标恢复策略。
无副作用的实时视图查找提取为 `HyperOsHostTree`，视图诊断格式化提取为 `HyperOsDiagnostics`；
原私有方法保留委托入口，避免破坏现有工装。12 项 Android View/反射检查覆盖嵌套、脱离、外来宿主及 slot。
后续 `HyperOsIconLease` 接管原生图标的弱引用 bookkeeping、锁屏 block list、延后隐藏、
电池子视图交还与排队刷新。TrioHooks 保留原 9 个私有方法委托入口及 Hook 安装/调用位置；
配置与反射字段在回调执行时读取，不在排队时冻结。原有 COLLAPSED/KEYGUARD_ROWS 入口引用同一实例的 map。
17 项历史交接序列在提取前后输出相同，新增 9 组 JUnit 直接执行真实生产类；
桌面 View 队列与模拟 OEM manager/meter 只覆盖协议，不能代替 HyperOS 固件真机验证。
`HyperOsReadout` 保存 HyperOS 功率换算、读数文本、行数和舍入规则；BatteryPresence 与 TrioHooks 保留原委托函数。
调用时传入标量读数及显示选项，纯类不依赖 Android/Xposed。640 组固定输入与旧实现的文本/行数一致，
原 44 项读数断言继续通过当前生产委托执行。采样、时钟、TextView 尺寸/预留和 ColorOS 环外横排文本仍由原路径负责。
后续只在有对应回归保护时提取具体职责，不把大文件拆分与 OEM 行为改写捆绑。
`TrioRenderer`/`TrioGeometry` 的绘制数学保持原实现；桌面 shim 和真实 Android Canvas 是互补检查，
当前没有跨系统/字体环境统一的 GPU golden，因此不宣称桌面快照可代替真机像素验收。

配置见 [配置协议](CONFIGURATION.md)，测试见 [测试](TESTING.md)，正式发布见 [发布](RELEASING.md)。
