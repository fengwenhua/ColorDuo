# ColorDuo / ColorOS 16 适配

ColorDuo 基于 HyperDuo 上游 `e9dffbc`，完整接入此前的 ColorOS 适配提交 `cd80bd7`。
修改日期：2026-10-24。保留上游历史、GPL-3.0 与原作者版权；修改版由 fengwenhua 维护。

## 项目标识

- 项目与应用名称：ColorDuo。
- 源码及更新仓库：<https://github.com/fengwenhua/ColorDuo>。
- 包名 / namespace：`io.github.fengwenhua.colorduo`。
- Xposed 入口：`io.github.fengwenhua.colorduo.ColorDuoModule`。
- 应用入口：`ColorDuoApp`；设置与广播使用独立的 ColorDuo 标识。
- 首版：`1.0.0 / 10000`。

从 `io.github.yixing233.hyperduo` 切换属于安装另一个应用，不会覆盖升级或自动迁移配置。
请停用旧模块作用域后再启用 ColorDuo，避免同时修改系统界面。

## 实测样本

Find N5 PKH110，固件 `PKH110_16.0.10.501(CN01)`，ColorOS 16（属性 V16.1.0），
Android 16 / API 36，SystemUI 16.99.12，LSPosed 2.2.1（7912）/ API 102。

接入电池圆环与自由布局、胶囊、Wi-Fi/普通单卡信号、已知网络类型、时钟、共享字体、
环外类型/信号及温度/功率。配置按原子快照更新，广播校验应用 UID 和包名。
只有实际绘制成功的组件才取得原生图标的显示权，停用、失效和配置变化时释放。

测试范围与历史适配包、改名构建的区别见
[验证记录](validation/coloros16-2026-10-24.md)。私人截图、手机原始日志和 OEM 反编译文件不随源码发布。

## 构建与检查

使用 JDK 21、Gradle 9.8.0、Android SDK Platform 37；target SDK 36。

```sh
gradle :app:assembleDebug :app:assembleRelease --no-daemon --console=plain
bash tests/coloros-observer.sh
```

默认版本 `1.0.0 / 10000`，可传 `-PcolorduoVersionName=1.0.0 -PcolorduoVersionCode=10000`。
默认 `colorOsStage=outside` 开启当前适配；`-PcolorOsStage=observe` 只安装诊断钩子。
按 ROM 选择适配器，保留 HyperOS 的原 TrioHooks。

安装 debug 包后可运行 Android 探针（release 中不包含 instrumentation）：

```sh
adb shell am instrument -w -r io.github.fengwenhua.colorduo/.CodecInstrumentation
```

字体通过 libxposed API 102 的只读 remote file 接口传递，后台校验摘要和解析；绘制时不读文件。
签名沿用上游公开的构建测试材料及其 `hyperduo` alias。新包名不与旧 HyperDuo 覆盖升级。

## 已修复的显示问题

1. 「连接 Wi-Fi 时隐藏网络类型」同样作用于环内组件优先级，按区域选择实际可绘制的替代项。
2. 原始网络读数与原生图标接管计划分开，避免让出某个图标后丢失连接状态、改变同一帧的排序结果。
3. 信号强度、网络类型文字与 Wi-Fi 切换按目标 View 增量交接，减少原生整组恢复带来的抽动。
4. 移动组空白时释放 inactive overflow dot 的占位，保留原生 overflow 和可见辅助内容。
5. 信号已融合后隐藏四种已确认的普通 data_inout 箭头，方向更新时同步保持隐藏；放权时遵循最新原生可见性。
6. 当前快捷编辑卡片直接使用最新设置，同一样式下调整排序后立即更新。

## 当前边界与下一步

- 当前真机接管范围为普通单卡 Binder；双卡、特殊 Binder、未知类型和警告状态交由原生。
- 原生外置电量百分比和充电图标继续由系统显示，避免重复绘制对应内容。
- 真快充和低电量显示尚需实测；无网络类型的真实双网关闭、VoLTE/漫游变化及流体云拥挤需补验。
- 当前 qsSplit 是未显示的零尺寸宿主，不计该布局通过；空间不足时保留原生对应图标。
- 正常字体发布保留最多三代，发布前进程异常退出留下的孤立文件尚不自动清扫。
- 历史 RingSlotsTest 的 empty-layout 断言在未修改的上游 HEAD 同样失败，未列为已通过。
- 新包名下175项 Android 检查、release 安装和重新启用后的 SystemUI 实际绘制已通过；完整折叠、锁屏/QS和整机重启恢复仍需验收。

## 发布

源码仓使用 `v1.0.0` tag；模块仓按官方格式使用 `10000-1.0.0` tag，标题 `1.0.0`，
Release 正文取 [release-notes-1.0.0.md](release-notes/release-notes-1.0.0.md)，两边上传同一 APK。
模块仓仅放 SUMMARY、README 与 Release，源码在 ColorDuo 主仓保留。

PowerShell 发版默认只写 ColorDuo 主仓；模块仓创建后显式传
`-ModuleRepo Xposed-Modules-Repo/io.github.fengwenhua.colorduo`。未申请完成前不会写入原作者模块仓。
