# Wi-Fi 与数据同时关闭时的网络类型残留

本项在 1.1.3 两仓发布并校验完成后开始，独立于已验收的 issue #3 信号接管修复。
Find N5 读取到 mobile_data=0、wifi_on=0，原网络 dump 仍记录 type=5G，说明制式来自 SIM 驻网，不能代表已启用移动数据。

新增有生命周期的 ColorOsDataEnabledSource：观察系统全局设置的 data/default-data-SIM 变化，
使用默认数据订阅的 TelephonyManager.isDataEnabled 读取真实开关并缓存；没有在每次 prepare/draw 时发起 Binder 查询。
全局表观察后按 key 过滤，覆盖 mobile_data、mobile_dataN、multi_sim_data_call 和飞行模式，
避免将每订阅开关的兄弟 URI 误认为 mobile_data URI 的子节点。
仅有模块启用、存在宿主且当前/充电/快充任一场景需要类型时观察；停用时注销。
无法读取时保留原观察标签，不把未知权限/机型状态猜成数据关闭。

类型 Plan 同时保存“已知关闭数据”的抑制位，贯穿复制、帧一致性与已绘制后接管：
关闭时去掉实际标签，并在已绘制帧后隐藏旧原生 5G；即使没放信号点阵，也不会把 5G 移到环外继续显示。
圆环、胶囊、环外标签均使用同一结果；蜂窝强度仍按 1.1.3 策略处理。

回归包括开关序列、未知状态、订阅 key、Plan 复制/帧变化，以及无点阵时两种样式的原生类型抑制。
1.1.4 为后续同签名真机候选，未公开发版。
