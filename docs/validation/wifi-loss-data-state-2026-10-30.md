# Wi-Fi 保持开启、连接断开后网络类型留空

## 已保留的现场

维护者给出触发：Wi-Fi 与移动数据均开启，从家中离开覆盖范围，Wi-Fi 开关仍开、没有连接，圆环上部留空；手动切换移动数据后恢复。

Find N5、ColorOS 16、SystemUI 16.99.12，模块 1.1.19。ADB 初始现场仍连着 Wi-Fi，**模块 network/data enabled=false，但系统 mobile_data 及当前订阅设置均为 1，TelephonyRegistry 当前数据订阅为 1、对应 mUserMobileDataState=true**。原生当前类型是 5G、缓存与原生模型资源一致。此时 Wi-Fi 遮住了失效的蜂窝类型；原始证据仅存忽略目录 `.local/wifi-loss-2026-10-30/`。

这次实际捕获到过期的“用户关闭数据”缓存，不将之前上部空白全部归因于同一个原因。

随后维护者在 WLAN 详情断开连接，未切移动数据，确认圆环上部留空。ADB 同时确认 wifi_on=1、mobile_data=1，cmd wifi 为 enabled／not connected，模块 wifiLevel=-1、enabled=false，系统原生类型仍是 5G。亮屏内屏截图也记录上部空白，触发与缓存错判已在同一现场对上。

## 原因与修复

- 原状态源只在启动及 Settings.Global 相关通知时读取 TelephonyManager.isDataEnabled。设置通知可能先于电话服务更新，读到旧值后没有电话服务回调来纠正；SIM 尚未就绪时也被缓存为 false，且缺少 SIM 就绪通知。
- API 31+ 注册默认数据订阅的 UserMobileDataStateListener，初始与后续电话服务回调驱动缓存。已绑定时 Settings 通知不再用可能较早的快照覆盖实时回调。
- 订阅变更监听与默认数据订阅系统广播负责重新绑定；无有效默认订阅、服务不可读时为未知，不猜成用户关闭。旧订阅／停用后排队的回调通过对象身份与订阅校验拒绝。
- 停用时注销电话、订阅、广播与设置监听；权限不足或旧系统保留设置观察回退。绘制路径没有新增 Binder 读取、轮询或重试定时器。
- 追加 live 与 subscription 的脱敏诊断，便于区分实时回调和回退路径。

## 自动验证

新增八项编译完整生产状态源的主机测试，覆盖设置先通知后电话更新、启动无 SIM 后就绪、默认 SIM 切换／旧回调、Wi-Fi 断开后的类型恢复及真实数据关闭、停用恢复、重复通知不重复订阅或读取 Binder、权限回退及旧 API／拔卡场景。

完整 JUnit 195 项（Release app 57／render 81／host 57）、Python 22 项、Release 构建、签名集成与 Observer 不打包检查通过；Lint 0 errors、34 warnings、1 hint。以旧生产状态源执行相同的乱序通知用例，实测失败为 expected true／actual false；修复后的完整生产源通过。

## 同签名候选与真机验收

- 源码 `223cd6f8885dde44d48c8e6be5ac528d74939497`，tag `v1.1.20`，版本 `1.1.20 / 10120`。
- [main CI 40990482717](https://github.com/fengwenhua/ColorDuo/actions/runs/40990482717)、[tag CI 40990482312](https://github.com/fengwenhua/ColorDuo/actions/runs/40990482312)、[同签名候选 40990543169](https://github.com/fengwenhua/ColorDuo/actions/runs/40990543169) 均成功；publish 跳过，artifact `12948798981`。
- APK SHA-256：`d1628b146cd367f2add07d5aa2836ef83cf52e55a56a61a4bd2e7ecc57e88abf`。固定正式证书、v2/v3、签名清单、版本与源码身份通过校验；安装后回拉 APK 一致，覆盖安装前后偏好完全相同。
- 仅重载实际 UID 10168 的 SystemUI，PID `31965 → 6092`。数据状态为 enabled=true、live=true、subscription=1；原 Wi-Fi 未连接、开关未关，圆环实际绘制的类型已恢复 5G，截图与 handoff 日志一致。
- 保持 Wi-Fi 开启且未连接，自动执行三轮移动数据关闭／开启，共六个状态；每次回调均准确更新，关闭时上部留空、开启后恢复 5G，截图核对通过。最后恢复移动数据开启，未操作 Wi-Fi 总开关。
- 维护者按圆环／胶囊核对请求回复“看着没问题了”。最后为内屏 2248×2480、圆环、120%、总开关开启，Wi-Fi 开启未连接、移动数据开启。最终截图中 5G 及信号点阵正常，原生移动信号未重复出现，状态栏及镜像 error=None，SystemUI 仍为 PID 6092。
- 原先现场缓存错判及旧代码乱序通知失败均有证据；这次没有再次模拟物理出门／基站切换、真实双卡切换或整机重启，相关分支由主机测试覆盖，不据常规真机回归声称所有系统情形均已验收。

**1.1.20 已作为正式版在两仓公开发布为 Latest**，八个公开附件回读一致，正式 APK 与手机已安装验收包相同，无须重装。签名升级提醒已保留，详见 [1.1.20 正式发布记录](release-1.1.20-2026-10-30.md)。
