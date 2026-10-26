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
## 同签名包与设备验收

- 修复提交 7c39969，候选源码 35223e73e4c96c7deac3cc9efcb76748f260ed82 / v1.1.4，版本 1.1.4 / 10104。
- Actions 40272306296 完整 build/sign 成功，初次 publish 关闭；本机独立复核 APK、源码清单、固定证书和 v2/v3。
- APK SHA-256：6e9baf07c4a02e9cefc3e1f41a651726c40f4ed9b7a04d6dbec384f89b1373f0。
- 本机完整门禁通过，新增 Plan 测试再次执行后 app 为 33 项、无失败；云端覆盖最终充电/快充监听条件。
- Find N5 覆盖安装前后偏好 XML 逐字节相同，SystemUI 重载后 PID 为 2512。
- 双关：network/data observing=true enabled=false；实际 handoff wifi=-1、mobile=4、type 为空，原生 type 已接管隐藏。
  截图顶部无 5G，底部信号点阵仍正常。
- 开启流量：观察值立即为 true，新已绘制 handoff 显示 type=5G；无需重启或切换样式。
- Wi-Fi 开启/数据关闭：wifi=4、mobile=4、type 为空，保留 issue #3 的融合行为。
- 恢复双关后，维护者按圆环/胶囊及总开关操作请求检查，回复“看起来正常了，帮我发布吧”。
  本轮手机临时 USB 亮屏设置已恢复，网络开关均恢复为测试前关闭。

按最新授权发布 1.1.4，推广同一组云端签名产物，不重编译验收 APK；历史 Release 保留。
