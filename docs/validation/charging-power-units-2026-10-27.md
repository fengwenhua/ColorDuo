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
同签名候选版本为 1.1.6/10106，由 Actions 生成。

## 同签名真机验收

源码 dedcbc4，普通 CI 40356624224 和签名候选运行 40356622348 通过。
候选 APK SHA-256：`149e9912446e59ebc81898634052f9fdd5335434e70ca017e50ba7715d9aa04a`。
固定生产证书、v2/v3、签名清单及版本均核验后，通过 adb 覆盖安装，配置保留。

加载后 SystemUI 实际采样 `watts=1.1322`，随后为 `1.419869`，不再是原来的千分之一瓦。
圆环宿主 209×52、环外 124；切换胶囊后宿主 190×65、环外 79，均 pending=false / failed=false，
保持 DRAWN。安装时明确重载一次系统界面；后续切换仍为同一进程 32375。
维护者已操作并确认圆环、胶囊均正常，随后授权正式发布 1.1.6。

正式发布使用同一源码 tag v1.1.6；发布说明明确继续修复 issue #4 的功率显示问题，
并保留低于 1.1.0（不包含 1.1.0）需卸载重装的提醒。

正式发布运行：[40358463996](https://github.com/fengwenhua/ColorDuo/actions/runs/40358463996)。
该运行重新签署的 APK 与已在手机验收的候选 APK 逐字节一致；正式 APK SHA-256 仍为上面的 149e9912…。
发布附件使用正式运行的 APK、摘要、清单与清单签名四件套，两仓下载需与该四件套逐字节核对。
模块仓首页与源码仓 module-README.md 同步为 1.1.6，并保留 1.1.3～1.1.5 的历史修复说明。
