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
