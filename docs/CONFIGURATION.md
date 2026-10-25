# 配置协议与设置更新

设置页通过 `BufferedSettings` 读取内存编辑视图，预览不等待磁盘或 LSPosed Binder。
整数滑块按 80 ms 合并；松手强制提交，开关、布局和离散选项立即排队。
Activity 停止时等待工作线程完成（最长 2 秒）；服务重连请求完整重放。
Android 可以直接终止进程，不能承诺在未执行生命周期回调时保留尚未刷新的 80 ms 编辑窗口。

`SettingsEditQueue` 保留正在发布的预览覆盖层。旧事务成功或失败均不会覆盖用户稍后输入的值；
失败批次保留到后续编辑、停止回调或服务重连重试，不在 Binder 故障时忙循环。

每个批次先冻结一个设置快照，再按 ROM 选择通道：

- ColorOS：一次本地事务（编辑值、epoch、revision、packet），一次远端 packet 事务，一次认证快照广播。
- HyperOS：一次本地事务，一次远端完整键值事务，一次兼容 Bundle 广播。
- 服务未连接：先保存本地，重连后完整同步。

只有两次写入都成功后才通知。广播接收器要求模块签名权限；ColorOS 另校验发送者身份和 revision。
不再从一次 UI setter 同时发送两种 ROM 的广播。

验证：`SettingsEditQueueTest` 覆盖合并、并发编辑、失败重试和删除；
`SettingsPublicationTest` 以计数器验证本地→远端→通知各一次及失败截断。
Android `SettingsBufferProbe` 在独立文件中验证 240 次输入合并为一次持久化与最终值落盘，
不接触真实用户配置。该突发测试不代替真人两秒拖动的流畅性检查。

## Schema 注册表

`SettingsSchema` 声明 61 个持久化模型字段：key、线类型、默认值读取器、范围（含样式相关上限）、
样式/充电维度、分组和格式版本。`TrioSettings.toBundle()` 由注册表生成，ConfigPacket JSON 复用该输出；
接收端先用注册表拒绝已知 key 的错误类型并约束数值，再执行历史迁移。
未知标量 key 忽略；不支持的 schema 版本明确拒绝。旧环布局、旧网络类型布尔值、胶囊继承圆环、
旧像素尺寸和时钟随数字字号回退仍由原读取器处理，避免迁移语义在重构中变化。

`SettingsSchemaTest` 自动枚举两种样式的每个字段，核对 SharedPreferences、Bundle、JSON、
copy、equality/hash 和局部更新。模型新增字段但未注册，或注册后漏改旧映射，测试均会失败。
序列化只声明一次；当前阶段仍保留历史读取、copy/equality 的直接字段实现，新增字段也必须通过这些门禁。
后续按注册表分组逐步提取 Content/布局/Appearance 模型，不一次替换绘制路径使用的快照。
