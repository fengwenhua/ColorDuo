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
后续只在有对应回归保护时提取具体职责，不把大文件拆分与 OEM 行为改写捆绑。
`TrioRenderer`/`TrioGeometry` 的绘制数学保持原实现；桌面 shim 和真实 Android Canvas 是互补检查，
当前没有跨系统/字体环境统一的 GPU golden，因此不宣称桌面快照可代替真机像素验收。

配置见 [配置协议](CONFIGURATION.md)，测试见 [测试](TESTING.md)，正式发布见 [发布](RELEASING.md)。
