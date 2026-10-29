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

**1.1.0 及以上新签名正式版可直接覆盖升级至 1.1.18，配置保留，无需卸载。** 旧测试签名的调试包因签名不同仍需卸载重装。

1.1.18 修复调高状态栏后，下拉和收回时图标短暂变回原始大小再放大的闪跳，同步过渡视图与面板顶部的尺寸和位置，保留系统原有动画；同时修复信号视图重新绑定后 Wi-Fi／5G 类型可能持续留空的问题。已在 Find N5 验证原始与自定义高度的动画，以及网络切换、双关恢复、圆环／胶囊和折叠场景。

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
