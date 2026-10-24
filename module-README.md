# ColorDuo

面向 ColorOS 16 的 LSPosed 状态栏模块，基于
[yixing233/HyperDuo](https://github.com/yixing233/HyperDuo) 修改。
把 Wi-Fi、移动信号与电量组合为圆环或胶囊，支持组件排序、网络类型、时钟、字体和环外读数。

## 实测环境

**OPPO Find N5（PKH110）· ColorOS 16 · Android 16 / API 36**，
固件 `PKH110_16.0.10.501(CN01)`，SystemUI `16.99.12`，LSPosed `2.2.1（7912）`。
当前实测为普通单卡，其他固件和网络组合需要单独验证。

## 安装

1. 从 [ColorDuo Releases](https://github.com/fengwenhua/ColorDuo/releases) 下载 APK。
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
修改日期：2026-10-24。沿用 [GPL-3.0](https://github.com/fengwenhua/ColorDuo/blob/main/LICENSE)，
保留上游版权及免责声明，修改版同协议公开源码。

Copyright (C) 2026 yixing233 与 HyperDuo 贡献者。

Copyright (C) 2026 fengwenhua 与 ColorDuo 贡献者。

本软件不提供担保，详见许可证第 15、16 条。
