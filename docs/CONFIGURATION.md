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

持续输入另由 `SettingsStreamProbe` 验证：在真实 Android 主线程用约两秒提交 240 次变化，
模拟每次持久化耗时 20ms，断言提交次数不超过 30、最终值准确保存。工作线程每次只消费一个批次，
不会在慢 Binder 事务结束时绕过下一批的 80ms 定时器；明确的松手/停止 flush 仍会立即刷出最终值。
测试保持本应用前台，使用独立文件，不修改用户布局。

字体快照查询只读取已完成的绑定，不发起 I/O；新字体导出在设置事务成功提交后启动，
避免导出线程先读取到旧路径而丢失首次绑定。重复失败由绑定标识抑制，服务重连可明确重试。

## Schema 注册表

`SettingsSchema` 声明 61 个持久化模型字段：key、线类型、默认值读取器、范围（含样式相关上限）、
样式/充电维度、分组和格式版本。`TrioSettings.toBundle()` 由注册表生成，ConfigPacket JSON 复用该输出；
接收端先用注册表拒绝已知 key 的错误类型并约束数值，再执行历史迁移。
未知标量 key 忽略；不支持的 schema 版本明确拒绝。旧环布局、旧网络类型布尔值、胶囊继承圆环、
旧像素尺寸和时钟随数字字号回退仍由原读取器处理，避免迁移语义在重构中变化。

`SettingsSchemaTest` 自动枚举两种样式的每个字段，核对 SharedPreferences、Bundle、JSON、
copy、equality/hash 和局部更新。模型新增字段但未注册，或注册后漏改旧映射，测试均会失败。
序列化只声明一次；当前阶段仍保留其他组的历史读取和直接字段，胶囊几何组已提取为不可变 CapsuleLayoutSettings；新增字段也必须通过这些门禁。
后续按注册表分组逐步提取 Content/布局/Appearance 模型，不一次替换绘制路径使用的快照。

## 胶囊几何配置组

`TrioSettings.capsuleLayout` 持有不可变 `CapsuleLayoutSettings`，成员为 leftDx/leftDy/rightDx/rightDy/length。
这五项的 SharedPreferences/Bundle 读取共用同一个默认值和范围入口，内部 Java/Kotlin 调用改为访问该组。
Schema 仍输出原五个扁平 key，VERSION 保持 1；老配置与环/胶囊样式维度、独立充电布局规则不变。
copy 共享不可变值，修改某一项生成新值；单 key 更新必须保留本组其他四项，不能直接替换为远端整组。
SettingsSchemaTest 自动展开 record 的五个逻辑字段，继续与完整 61 项注册表逐项核对。
其余 Content/环布局/Appearance 仍待按计划提取，不将第一组视为 O-08 全部完成。
