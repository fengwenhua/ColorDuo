# ColorOS 16 充电修复工作版本验证

验证包：`1.0.0-charge-fix / 10001`。本次修复用于正式版 `1.0.2 / 10002`；下列哈希属于验证包。
设备：OPPO Find N5 / ColorOS 16，沿用[迁移核对记录](colorduo-migration-audit-2026-10-24.md)的环境。

## 修改

1. 统一普通原生充电图标的显示权。ColorOS 适配器不再通过失效的 `showBolt=false` 修改渲染快照；
   模块成功绘制充电帧后隐藏 `battery_charge_icon`，同时释放其布局占位。
   精确跟踪 OEM 充电更新，保存最新原生可见性；关闭模块、脱离宿主、绘制失败或非充电帧时恢复。
   不接管反向充电图标。
2. 普通充电、快充的组件布局分别保存，圆环/胶囊也各自独立。未编辑的状态保留原有继承行为，
   显式编辑会覆盖该状态的旧「充电时中部显示数字」规则。字号、颜色等外观共用。
3. 状态贯穿设置保存、预览与快捷编辑小卡片、复制/比较、远端配置增量、Bundle/JSON和真实 Canvas 绘制。
   仅有快充标志而没有实际充电时，继续使用基础布局。

## 验证

- debug/release、R8、release lint vital 构建通过；release 签名验证通过，无 debuggable 标记。
- 原有纯 Java 检查82项通过。
- Android instrumentation 共240项通过：原有175项，加65项充电回归。
  新增项目覆盖真实 SharedPreferences、Bundle/JSON、普通/快充独立渲染像素、ColorOS Frame、
  原生图标显示权与最新状态恢复，以及实际 SettingsRepository 的编辑与保存路径。
- 真实 repository 验证临时设置普通充电数字、快充闪电并交叉修改组件，再恢复用户原配置。
  与测试前的本地备份比较，只有单调递增的传输 revision/snapshot 变化，用户显示设置无变化。
- release 修复包已通过 ADB 安装。SystemUI 从 PID5787 重载到17545，
  日志确认 `stage=outside coreReady=true`，桌面宿主实际进入 `DRAWN`，85×52。
- 重载时手机已满电：`charging=false quick=false plugged=true full=true`，
  原生普通充电图标为 GONE，模块未持有它。该状态不能用于证明真实充电时的最终视觉结果。

## 包

本地产物：`dist/ColorDuo-1.0.0-charge-fix.apk`，3,544,952字节。

SHA-256：`123ad3f8cd5f95e87287a6f4991bfa23c4af012bbd52e0108ccce43f1c127dca`。

## 尚待真机确认

首次重载时满电，用户随后在实际充电时重新连接，完成了下面的桌面检查。
用户后续已报告真实快充正常；仍需确认停止充电及这些状态下关闭/开启模块的显示与占位恢复，
并检查折叠和 QS 回切。未把 Canvas 模拟状态计为真实快充验收。

## 后续：真实普通充电桌面检查

- 用户确认「充电中了，看着好像没有闪电标识了」。
- 两次读取均为 `charging=true quick=false plugged=true full=false`，确认是实际普通充电。
- 桌面 `chargeIcon owned=true nativeVisibility=0`：OEM 原本要求显示的普通闪电已由模块接管。
  对应 `battery_charge_icon` 为 GONE。
- 桌面电池宿主宽89px，内含85px宽的模块图标及边距；原生充电图标旧边界为89–125px，
  已不计入当前宿主宽度，额外36px占位已释放。GONE 子视图仍保留旧边界，不代表它仍占空间。
- SystemUI 仍为PID17545；桌面持续实际绘制，读取时已累计150次。
  本次采样的500条 SystemUI logcat 中未见 FATAL EXCEPTION，不外推为全程无异常。
- 当前圆环基础布局仍为 `5,1|3|7|`，中部电量数字，没有闪电组件；
  用户尚未保存独立的普通/快充布局键。预览编辑独立保存已由240项检查覆盖，仍待用户操作界面确认。

## 后续：用户确认普通/快充独立编辑

- 用户操作预览中的快充布局并切回后确认「可以了，看着没问题」。
- 当前圆环基础布局仍为 `5,1|3|7|`；普通充电没有独立覆盖，继承其中部电量数字。
  新增 `quick_charging_layout=1;31;1;0;5,1|4,3|7|`，快充中部优先闪电，随后是电量。
  快充编辑没有覆盖基础布局或普通充电的数字。
- 胶囊另有普通充电配置 `1;31;1;0;5,1|3,4|7|6`，与圆环键分别保存。
- 应用发布快照revision44携带圆环的基础布局和快充独立配置；LSPosed 日志确认SystemUI按顺序接收40–44，
  最终 `snapshot applied revision=44 enabled=true source=framework`。
- SystemUI仍为PID17545，桌面累计191次实际绘制；普通充电时原生闪电仍由模块持有并保持GONE，电池宿主宽89px。
  本次2500条SystemUI logcat采样无FATAL EXCEPTION。
- 实际电源状态仍是 `charging=true quick=false`。本项确认界面编辑与配置传递，不计作真实快充验收。

## 用户确认真实快充

用户明确报告「我已经试过快充了，没问题」，并授权提交、推送和发版。
将此记录为用户实机视觉验收；本轮没有采集真实快充时的 ADB 状态，保留这一证据区别。
