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

**1.1.0 及以上新签名正式版可直接覆盖升级至 1.1.6，配置保留，无需卸载。** 旧测试签名的调试包因签名不同仍需卸载重装。

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
