# Issue #3：Wi-Fi 开启、移动数据关闭时原生信号回归

来源：<https://github.com/fengwenhua/ColorDuo/issues/3>。反馈设备 PLQ110，ColorOS 16 / SystemUI 16.99.12，模块 1.1.2。
Find N5 已通过 ADB 连接，同一系统界面版本；测试前读取到 mobile_data=0、wifi_on=1。

## 根因与修复

JADX 确认 `MobileIconInteractorImpl$showExclamationMark$1` 的三项输入包含 dataEnabled；
关闭移动数据可使 AOSP showExclamationMark=true，即使 Wi-Fi 正常、蜂窝仍在网。
OplusMobileIconInteractorImpl 继承该标志，但单独从 defaultConnections 计算 inetCondition：
默认连接已验证或当前默认不是移动网络时取 1，否则取 0。
SignalIconModel.Cellular.toSignalDrawableState 的 OPlus 分支传入 inetCondition、inService 等实际状态，
不会将继承的 showExclamationMark 交给 getCurrentIconId。

旧模块直接因 showExclamationMark=true 拒绝接管，导致信号恢复原生。
改为读取/校验 OPlus 实际使用的 getInetCondition：仅接受 1，0/未知值保留原生。
继续拒绝无服务、运营商切换、软 SIM、非蜂窝模型、非 5 档或非法强度；HyperOS 路径未改。
兼容性符号探测同步要求 getInetCondition，缺失时仍回退，不靠猜测接受其他固件。

补充 Wi-Fi/数据开关/断网重连序列和所有强度/未知状态回归；原告警保护测试更新为实际 OPlus 状态。
完整 app/render/host 测试、Lint/release 在首次修复后通过。
网络现有有界 ledger 加入 dump，记录匿名 slot/inetCondition/接受强度和接管状态，便于真机核对；默认仍不输出高频日志。

后续按同签名候选包验证真机，不能将 PLQ110 反馈本身算作已验收。
