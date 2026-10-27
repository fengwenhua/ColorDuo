# ColorDuo

面向 **ColorOS 16** 的 LSPosed 状态栏模块，把 **Wi-Fi、移动信号和电量** 融合为圆环或胶囊图标。

本项目基于 [yixing233/HyperDuo](https://github.com/yixing233/HyperDuo) 修改，保留原项目的
渲染与设置界面，并增加 ColorOS 系统界面适配。ColorDuo 由 fengwenhua 维护，修改日期：2026-10-27。

## 实测设备

| 项目 | 实测环境 |
| --- | --- |
| 手机 | OPPO Find N5（PKH110） |
| 系统 | ColorOS 16，ROM 属性 V16.1.0 |
| 固件 | PKH110_16.0.10.501(CN01) |
| Android | Android 16 / API 36 |
| 系统界面 | SystemUI 16.99.12 |
| 框架 | LSPosed 2.2.1（7912），libxposed API 102 |

目前的真机验证基于上述固件和普通单卡场景。其他机型、固件和双卡组合需要单独验证；
无法确认的网络类型、特殊信号和异常状态交由系统显示。保留上游 HyperOS 适配路径，
本分支的验证重点是 ColorOS。

## 功能

- **首次安装默认布局**：首次打开使用 Find N5 实测的圆环、胶囊与充电状态配置；覆盖升级保留已有设置。
- **圆环与胶囊**：电量、Wi-Fi、移动信号、网络类型和充电状态可按位置组合，支持独立尺寸与外观设置。
- **独立充电布局**：在「预览状态 → 充电／快充」分别设置组件布局，例如普通充电显示数字、快充显示闪电；圆环与胶囊各自保存，字号和颜色共用。
- **组件排序**：上、中、下区域按优先级选择当前可显示的内容，不需要手动随网络切换布局。
- **Wi-Fi / 5G 切换**：上部按「网络类型、Wi-Fi」排序，并打开「连接 Wi-Fi 时隐藏网络类型」，
  连接 Wi-Fi 时显示 Wi-Fi，断开后显示可用的蜂窝网络类型；两者都不可用时该区域留空。
- **原生图标恢复**：对应内容真正绘制后才隐藏系统图标；停用模块或交还某项组件时恢复原生显示。
  已融合的移动信号会一并隐藏普通上下行箭头，避免残留三角形或占位。
- **胶囊时钟与字体**：支持时间更新、自定义字体导入及恢复系统字体。
- **环外组件**：可显示网络类型、信号条、电池温度和电池端充电功率。
- **VPN 高亮**：VPN 开启时，圆环／胶囊中的 Wi-Fi 及网络类型自动变色，信号点保持原色；在「组件 → VPN」自选颜色，默认蓝色，无需下拉状态栏触发。ColorOS 已绘制高亮的状态栏隐藏重复的原生 VPN 标识，交还系统时恢复。
- **实时预览**：调整组件和排序时，快捷编辑中的样式卡片同步更新；状态列表显示不全时提供滑动提示与翻页箭头。
- **插电显示稳定**：系统显示充电提示时保留三合一图标，圆环与胶囊不会短暂退回原生图标。
- **折叠屏适配**：已分阶段验证 Find N5 内外屏、桌面、锁屏与下拉快捷设置的布局和恢复。

![样式预览，来自 HyperDuo 原项目](docs/images/states.png)

上图为原项目的样式素材。ColorOS 上的实测范围与版本记录见
[ColorOS 适配说明](docs/COLOROS_ADAPTATION.md)和[验证记录](docs/validation/coloros16-2026-10-24.md)。
普通充电的原生闪电与占位恢复已检查日志，用户已确认真实快充显示正常。低电量等场景仍在补充验收。
充电修复的具体范围见[1.0.2 验证记录](docs/validation/coloros16-charge-fix-2026-10-24.md)和
[1.0.3 插电与外屏提示记录](docs/validation/coloros16-plug-style-2026-10-25.md)。

## 安装

**升级必读：低于 1.1.0 的旧版本（不包含 1.1.0）必须先卸载再安装新版。卸载会清除自定义配置，请提前记录布局；安装后在 LSPosed 重新启用 ColorDuo、勾选系统界面并重载。**

**1.1.0 及以上新签名正式版可直接覆盖升级至 1.1.7，保留配置，无需卸载。** 旧测试签名的调试包仍因签名不同而需要卸载重装。

1.1.7 修复 ColorOS 充满后仍显示温度和功率的问题。两项开启且勾选“温度仅充电时显示”时，依据真实充电状态成组显隐：开始充电一起出现，充满、暂停、拔线或数据不完整时整组隐藏。已在 Find N5 验证满电后的圆环和胶囊显示。

1.1.6 继续修复[源码仓 issue #4](https://github.com/fengwenhua/ColorDuo/issues/4) 反馈的充电功率显示为 0：修正 ColorOS / OPlus 电池电流单位换算，功率随实时电流更新，保留温度显示和 HyperOS 的原有换算。已在 Find N5 验证圆环和胶囊显示。

1.1.5 修复开启充电功率后，插电或读数尺寸变化导致圆环／胶囊回退原生图标的问题，见[源码仓 issue #4](https://github.com/fengwenhua/ColorDuo/issues/4)。已在 Find N5 验证拔插与折叠切换。

1.1.4 修复 Wi-Fi 与移动数据同时关闭时，圆环／胶囊仍显示 5G 等网络类型的问题；重新开启移动数据后自动恢复制式显示。

1.1.3 修复 Wi-Fi 开启、移动数据关闭时原生移动信号重新出现在圆环／胶囊左侧的问题，见[源码仓 issue #3](https://github.com/fengwenhua/ColorDuo/issues/3)。

### 前置条件

| 项目 | 要求 |
| --- | --- |
| 系统 | 当前实测为 Find N5 / ColorOS 16 / Android 16 |
| 权限 | 已 root，并安装兼容的注入环境 |
| 框架 | 支持 libxposed **API 102** 的 LSPosed |
| 作用域 | 系统界面 `com.android.systemui` |

APK 的最低 Android 声明为 API 29；这不代表每种 Android 版本的系统界面均已适配。

### 步骤

1. 从 [ColorDuo Releases](https://github.com/fengwenhua/ColorDuo/releases) 下载 `ColorDuo-*.apk`。
   正式 APK 由受保护的 GitHub Actions 签名；普通 CI artifact 为 unsigned，供开发验证使用。
2. 安装后，在 LSPosed 中启用 **ColorDuo**，勾选 **系统界面（`com.android.systemui`）**。
3. 重启系统界面或手机，使模块首次加载。设置页顶栏的重启按钮需要 root 授权。
4. 打开桌面的 **ColorDuo**，设置圆环或胶囊及组件布局。后续配置修改会实时生效。

也可以用命令重启系统界面：

```sh
adb shell "su -c 'killall com.android.systemui'"
```

### 从 HyperDuo 或早期适配包切换

ColorDuo 包名为 **`io.github.fengwenhua.colorduo`**，与原版及此前的
`io.github.yixing233.hyperduo` 适配包是两个独立应用，不能覆盖升级，配置和 LSPosed 作用域不会自动迁移。

先在 LSPosed 中停用旧 HyperDuo 对系统界面的作用域，再启用 ColorDuo 并重启系统界面。
不要同时启用两个模块修改同一组状态栏图标。旧应用未卸载时，其原有配置仍保存在旧应用中。

## 常见问题

**改完设置需要重启吗？**

通常不需要，组件、排序、尺寸和字体通过远程配置更新。首次启用、更新模块代码后，
需要重新加载系统界面。

**连接 Wi-Fi 后还显示 5G？**

检查「网络类型 → 连接 Wi-Fi 时隐藏网络类型」是否打开，并在目标区域把「Wi-Fi」排在
「网络类型」之后作为可用的替代组件。关闭此选项时，继续按原有固定优先级显示。

**关闭模块后系统图标能恢复吗？**

总开关及各组件交还系统时会释放对应原生图标。若另一模块也在修改相同图标，先排除作用域冲突。

**支持双卡、VoLTE、漫游或其他 ColorOS 手机吗？**

普通单卡是当前的实测接管范围。双卡及特殊 Binder 路径、VoLTE/漫游等辅助图标保留原生处理，
不能由 Find N5 单卡验收推断其他组合。兼容范围会随实际验证更新。

**温度和功率来自哪里？**

功率取电池端电流幅值乘以电压，不是充电器标称或协商的最大功率。未插电时不显示功率。
充电、快充状态跟随系统读数。

**检查更新会下载原作者的 HyperDuo 吗？**

不会。应用内仓库、更新查询和下载均指向
[fengwenhua/ColorDuo](https://github.com/fengwenhua/ColorDuo)。仓库尚无正式 Release 时会提示没有发布版本。

## 开发与验证

需要 **JDK 21、Android SDK Platform 37 和对应 Build Tools**；仓库内 Wrapper 固定 Gradle 9.8.0。
配置好 `JAVA_HOME`、`ANDROID_HOME`，在本仓库运行：

```sh
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --console=plain
bash tests/coloros-observer.sh
```

构建版本由 `version.properties` 统一维护，可用 `-PcolorduoVersionName=...` 和
`-PcolorduoVersionCode=...` 覆盖。默认启用完整的当前 ColorOS 适配阶段；
只读诊断构建可传入 `-PcolorOsStage=observe`。

产物位于 `app/build/outputs/apk/`；本地 release 为 unsigned，正式签名私钥仅在受保护云任务使用。
仓库包含上游构建所需的 libxposed Maven 文件与仅供 debug 使用的公开测试签名材料；
本机 SDK、构建缓存、APK、手机日志及截图不入库。

新包名下的 Android 配置与绘制探针可以在安装 debug 包后执行：

```sh
adb shell am instrument -w -r io.github.fengwenhua.colorduo/.CodecInstrumentation
```

诊断日志可按模块名称过滤：

```sh
adb logcat -v time -s LSPosedFramework:* | rg ColorDuo
```

更多说明见[文档索引](docs/README.md)、[ColorOS 适配说明](docs/COLOROS_ADAPTATION.md)和
[开发入口](docs/DEVELOPMENT.md)。历史 HyperOS 文档、截图和发布说明保留原有名称与实测背景。

## 致谢与许可证

- 原项目：[HyperDuo](https://github.com/yixing233/HyperDuo)，作者 **yixing233** 及其贡献者。
- 设置界面：[Compose](https://developer.android.com/compose) 与 [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)。

沿用 **GNU General Public License v3.0（GPL-3.0）**，全文见 [LICENSE](LICENSE)。
原作者版权声明、Git 历史、许可证与免责声明均保留；ColorOS 适配及 ColorDuo 修改同样以 GPL-3.0 发布。

Copyright (C) 2026 yixing233 与 HyperDuo 贡献者。

Copyright (C) 2026 fengwenhua 与 ColorDuo 贡献者。

分发修改版 APK 时须按同一协议提供对应源码，并保留原有署名和免责声明；发布版本可通过本仓库
对应的提交或 tag 定位源码。本软件不提供担保，详见 GPL-3.0 第 15、16 条。
