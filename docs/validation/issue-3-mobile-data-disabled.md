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

## 同签名包与真机验收

- 修复提交 cdb9f0a，候选源码 6052157 / v1.1.3，版本 1.1.3 / 10103。
- Actions 40269688801 的完整 build/sign 成功，publish 显式关闭；同期普通 CI 40269637840、40269637803 均成功。
  签名 APK/清单/源码提交、固定生产证书及 v2/v3 由本机独立复核。
- APK SHA-256：5625828aa10d63d73205cc93a7a727870f6c393085cfc2df788e2a570b4c361a。
- Find N5 覆盖安装成功，未卸载，安装前后偏好 XML 逐字节相同。重载 SystemUI 后网络 Hook 正常安装。
- 数据关闭且 Wi-Fi 开启时，inetCondition=1、acceptedLevel=4；实际已绘制的 handoff 同时记录 wifi=4、mobile=4。
  修复前截图左侧有原生四格信号，修复后该图标消失、圆环底部出现信号点阵。
- 经 ADB 开启再关闭移动数据，恢复 Wi-Fi 开启/数据关闭的原状态，接管继续正常；没有额外重启 SystemUI。
- 维护者解锁后按要求操作并反馈“看样子修复了”。未将该反馈扩展为其他机型或未单独记录场景的实机结论。
- USB 保持亮屏为本次临时设置，检查结束恢复原值；原来的 Wi-Fi/数据开关状态已恢复。

本机第一次完整门禁通过；补充有界 dump 后的本机重复压缩任务在完整云端 build 成功后主动停止，
未将该停止伪装成本机构建成功。最终候选来源为上述云端成功的正式签名产物。
当前只有测试候选包，没有公开 1.1.3 Release。后续发版须明确标注修复源码仓 issue #3；
现有 release-notes-1.1.3.md 已包含该条目。PLQ110 仍只有 issue 反馈，不计为本人实机验收。
