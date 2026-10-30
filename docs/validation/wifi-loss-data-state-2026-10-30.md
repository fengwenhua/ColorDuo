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

同签名候选及准确断连场景修复后验收结果待补充；当前公开版本仍为 1.1.19。
