# issue #4：ColorOS 充电功率单位

本项处理 [issue #4](https://github.com/fengwenhua/ColorDuo/issues/4) 在 1.1.5 之后继续反馈的“充电功率显示为 0”。
1.1.5 处理的是开启读数后圆环/胶囊在尺寸变化时回退原生图标；电流单位是另一处问题。

## 真机证据与来源

Find N5 / PKH110 / ColorOS 16，已安装 1.1.5，通过 USB 连接并处于充电状态。
BatteryManager 实际电流连续采样为 -651、-689、-726、-663、-669，电池电压约 4415～4429 mV，
电池 charge counter 持续上升。电池节点 current_now 同样为数百的负值，model_name 为 `oplus battery`。
修复前 SystemUI 自身 dump 中读数为 `watts=0.0017873049`，绘制阶段保持 DRAWN，格式化后成为 `0.0W`。
因此是换算问题，并非圆环再次退出接管。

[OnePlus 官方 sm8750 / Android 16 内核源码](https://github.com/OnePlusOSS/android_kernel_modules_and_devicetree_oneplus_sm8750/blob/oneplus/sm8750_b_16.0.0_oneplus_13/vendor/oplus/kernel/charger/v2/oplus_chg_gki.c#L793)
的电池 CURRENT_NOW 直接上报 GAUGE_ITEM_CURR，没有像 USB 电流那样乘 1000。
这条电池路径使用毫安，和 [Android BatteryManager 的微安约定](https://developer.android.com/reference/android/os/BatteryManager#BATTERY_PROPERTY_CURRENT_NOW) 不同。
本机接口及电池计数变化也与毫安相符。

同时验证了两个不能替代实时电池采样的字段：

- 附加充电广播的 chargewattage 为 0，而 cpa_charge_wattage 为 10，是充电协议相关字段。
- 附加广播的 level 仍为 89，标准电池广播已经为 92，其 batterycurrent 也不是每三秒更新。

临时诊断 APK 仅查询电池 API 和这两个电池广播；原始资料存于忽略目录 `.local/power-zero-device`。
普通应用无法读电池 sysfs；正式修复继续使用系统电池 API，不要求 root 读文件。

## 修复

ColorOsReadout 独立封装这条 OPlus 路径的毫安 × 毫伏换算，ColorOsReadoutSource 的后台采样调用它。
-651 mA × 4415 mV = 2.874165 W，显示为 `2.9W`。
不根据电流大小切换单位，避免低电流与快充穿过阈值时发生千倍跳变。
Integer.MIN_VALUE / 无效电压保持未知，真实零电流仍可显示 0.0W；正负电流均取幅值。
HyperOS 的微安换算、读数格式、宿主接管和布局代码未改。

这是当前 ColorOS 适配器依赖的 OPlus 电池路径修复；另一台反馈设备尚无本轮实机取证。

## 自动验证

新增五组 JUnit：真实毫安样本与实际格式化、小/大电流同一单位、未知与真实零值、温度组合、HyperOS 微安不受影响。
app/render/host 共 40/69/41，即 150 个 JUnit 方法/实例；Lint 与 release 构建通过。
同签名候选版本为 1.1.6/10106，由 Actions 生成；未授权正式发布。
