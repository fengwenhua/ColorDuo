# ColorDuo

面向 ColorOS 16 的 LSPosed 状态栏模块，基于
[yixing233/HyperDuo](https://github.com/yixing233/HyperDuo) 修改。
把 Wi-Fi、移动信号与电量组合为圆环或胶囊，支持组件排序、网络类型、时钟、字体和环外读数。
普通充电和快充的组件布局可分别保存，圆环与胶囊也各自独立。
插电显示系统充电提示时保留三合一图标；外屏预览状态显示不全时提供滑动提示与翻页箭头。

## 实测环境

**OPPO Find N5（PKH110）· ColorOS 16 · Android 16 / API 36**，
固件 `PKH110_16.0.10.501(CN01)`，SystemUI `16.99.12`，LSPosed `2.2.1（7912）`。
当前实测为普通单卡，其他固件和网络组合需要单独验证。

## 安装

**升级必读：低于 1.1.0 的旧版本（不包含 1.1.0）必须先卸载，再安装新版。卸载会清除配置，请提前记录布局；安装后在 LSPosed 重新启用 ColorDuo、勾选系统界面并重载。**

**1.1.0 及以上新签名正式版可直接覆盖升级至 1.1.30，配置保留，无需卸载。** 旧测试签名的调试包因签名不同仍需卸载重装。

1.1.30 继续修复重新开启移动数据时原生信号条闪现，圆环／胶囊网络切换已在 Find N5 对照录像验证。新增「组件 → 读数组 → 双电芯功率补偿 ×2」（默认关闭，已上报整组功率的设备不要开启），并改善默认上网卡选择。

**特别提醒：[#11 双电芯功率补偿](https://github.com/fengwenhua/ColorDuo/issues/11) 与 [#10 默认上网卡选择](https://github.com/fengwenhua/ColorDuo/issues/10) 缺少对应设备实测：维护者没有一加 15 及双卡测试环境，无法确认反馈设备上是否真实有效，目前仅通过自动化／模拟测试，请按需试用并反馈。#10 仅部分改善，两张卡同时合并进圆环／胶囊尚未实现。**

1.1.29 修复 Wi-Fi／移动数据切换、Wi-Fi 重连时，圆环／胶囊左侧短暂冒出原生 Wi-Fi 或移动信号的问题；下拉区域网络视图移除不再影响桌面接管。补齐「组件 → 外观 → 整体图标大小」入口，紧接三合一样式显示并实时生效；ColorOS 圆环支持 60%–150%（受可用行高限制），胶囊／矩形保留原范围。Find N5 的切换录像、下拉面板、大小恢复及关闭模块后恢复原生均已核对。

1.1.26 优化“上部网络、中部电量”和“上部电量、中部网络”两种圆环布局，按真实字形使用内部空间，中部网络类型不再跟随 Wi-Fi 过度缩小，网络字号最高可设 64。在「组件 → 外观 → 整体图标大小」中，ColorOS 圆环可放大至 150%，圆环及内容同步增大，受当前行高限制；恢复 100%、关闭模块或切换样式会归还原占位。Find N5 的原生绘制、间距、尺寸及恢复检查通过，维护者确认实际显示正常。长标签继续按可用空间适配。

1.1.25 修复圆环开口没有贴近电量数字或充电闪电的问题（[issue #8](https://github.com/fengwenhua/ColorDuo/issues/8)），开口随字号、字重和闪电大小实时适配，并修复加粗时字号反向缩小。在「组件 → 电量数字」新增「保持中部布局／为数字让位」：默认保持中部大小和位置；让位时中部组件自动移动或缩小，常规字体的上部数字可放大到 56，编辑点击和高亮区域同步调整。Find N5 原生绘制与两模式恢复检查通过；当前 134% Wi-Fi 布局下保持模式最大 37、让位模式可使用到 56，其他字体与布局按实际空间适配。

1.1.21 修复系统「电量百分比」设为「在电池外显示」时，圆环／胶囊内部电量留空、右侧保留重复数字的问题（[issue #7](https://github.com/fengwenhua/ColorDuo/issues/7)）。模块按自己的布局显示电量，关闭模块后恢复系统原有百分比；已在 Find N5 验证两种样式、三种百分比设置及关闭／恢复。

1.1.20 修复 Wi-Fi 总开关保持开启、离开覆盖范围后，圆环／胶囊上部持续留空，需要手动开关流量才能恢复的问题；实时同步默认数据 SIM 的移动数据开关，处理 SIM 初始化和切换后的状态恢复。已在 Find N5 验证 Wi-Fi 开启未连接时恢复 5G、三轮流量开关及圆环／胶囊显示。

1.1.19 修复开关 Wi-Fi 后，圆环／胶囊左侧原生 Wi-Fi 图标延迟消失，以及系统后续刷新时再次出现的问题；保留自定义状态栏下拉／收回的平滑动画，以及信号视图重新绑定后的 Wi-Fi／5G 类型恢复修复。

1.1.16 新增首页「状态栏高度」调节（100%–150%），整行图标与时钟同步放大并保持摄像头对齐；在「组件 → 外观」选择胶囊后实时调节宽度，左右靠两端，满电时保留时间与 100 电量的间距。修复恢复高度后的顶部挤压、右侧图标越界及下拉收回时的缩放抖动。已在 Find N5 验证内外屏与实时更新。

1.1.11 新增 VPN 高亮：VPN 开启时仅 Wi-Fi 图标与 5G 等网络类型文字变色，四个信号点和环外信号条保持原色；在「组件 → VPN」自选颜色，默认蓝色。ColorOS 对应区域实际画出高亮后，隐藏重复的原生 VPN 图标和下拉右上角标签，关闭三合一或没有高亮内容时恢复。保留相机、麦克风、定位等隐私图标与其他安全模式提示。已在 Find N5 验证圆环／胶囊、VPN 开关、颜色及原生标签恢复。

1.1.7 修复 ColorOS 充满后仍显示温度和功率的问题。两项开启且勾选“温度仅充电时显示”时，按真实充电状态成组出现或消失，已在 Find N5 验证圆环和胶囊显示。

1.1.6 继续修复[源码仓 issue #4](https://github.com/fengwenhua/ColorDuo/issues/4) 反馈的充电功率显示为 0：修正 ColorOS / OPlus 电池电流的单位换算，功率继续随实时电流更新。已在 Find N5 / ColorOS 16 验证圆环和胶囊显示。

1.1.5 修复启用充电功率后，插电或读数宽度变化导致圆环／胶囊回退原生图标的问题，见[源码仓 issue #4](https://github.com/fengwenhua/ColorDuo/issues/4)。已在 Find N5 / ColorOS 16 验证圆环、胶囊的 USB 拔插及内外屏折叠切换。

1.1.4 修复 Wi-Fi 与移动数据同时关闭时，圆环／胶囊仍显示 5G 等网络类型的问题；重新开启移动数据后自动恢复制式显示。

1.1.3 修复 Wi-Fi 开启、移动数据关闭时原生移动信号重新出现在圆环／胶囊左侧的问题，见[源码仓 issue #3](https://github.com/fengwenhua/ColorDuo/issues/3)。

1. 从[模块仓 Releases](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases) 或
   [ColorDuo Releases](https://github.com/fengwenhua/ColorDuo/releases) 下载 APK。
2. 在 LSPosed 中启用 **ColorDuo**，作用域选择 **系统界面（`com.android.systemui`）**。
3. 重启系统界面或手机，打开桌面 ColorDuo 配置组件；后续设置修改实时生效。

需要 root 和支持 libxposed **API 102** 的 LSPosed。
包名为 `io.github.fengwenhua.colorduo`，不能覆盖升级 HyperDuo，旧配置不会自动迁移。
切换前请停用旧 HyperDuo 的作用域，避免两个模块同时修改同一组图标。

## 使用说明与反馈

- [完整说明](https://github.com/fengwenhua/ColorDuo#readme)
- [问题反馈](https://github.com/fengwenhua/ColorDuo/issues)
- [ColorOS 适配范围](https://github.com/fengwenhua/ColorDuo/blob/main/docs/COLOROS_ADAPTATION.md)

本文件是模块仓首页的发布源文件；ColorDuo 的源码与下载主页是上述仓库。

## 致谢与许可证

原项目作者 **yixing233** 及 HyperDuo 贡献者；设置界面使用 Compose 与 Miuix。
修改日期：2026-10-27。沿用 [GPL-3.0](https://github.com/fengwenhua/ColorDuo/blob/main/LICENSE)，
保留上游版权及免责声明，修改版同协议公开源码。

Copyright (C) 2026 yixing233 与 HyperDuo 贡献者。

Copyright (C) 2026 fengwenhua 与 ColorDuo 贡献者。

本软件不提供担保，详见许可证第 15、16 条。
