# ColorOS 宿主接管

`HostDecision` 对不可变采样快照作纯决策；`ColorOsBatteryAdapter` 留在主线程采样 View、反射 OEM 状态并执行副作用。
决策按阶段进行，禁止把“准备挂载”当作“已绘制”：

1. Gate：缺失宿主或原生样式嵌套更新时等待；配置关闭、能力缺失和失败时释放。
2. Model：校验原生 Drawable、样式、电量、透明度和尺寸。
3. Preparation：尺寸变化先释放并安排布局；空间不足恢复原生。
4. Attachment：第三方 Drawable 保留所有权；新尺寸或首次挂载先 preflight，随后 MOUNT/UPDATE。
5. Presentation：只有当前 token/帧、配置启用、布局完成、宿主可见且网络计划匹配，才 COMMIT_LEASES。

原生样式更新期间不释放仍已成功绘制的帧；新插电的样式 2 仍由 `ColorOsPlugBridge` 限定，
不把任意隐藏策略视为充电动画。布局等待保留原先区别：模块主动 resize 可以暂留既有接管，
普通原生布局等待则释放。Drawable 绘制失败的回调继续立即释放并设置失败状态。

`HostDecisionTest` 覆盖首次帧、折叠尺寸、1→2→1、无桥接样式 2、关闭/不支持、第三方替换、
旧帧回调及网络计划切换。Android Canvas 探针验证实际 Drawable 绘制状态、失败恢复和 leases。
这些测试不代表其他 ColorOS/HyperOS 机型已验证。

## 插电后立即折叠

Find N5 实测暴露了组合场景：样式 2 已由新插电事件确认，但紧接着的 configuration 回调会
释放 Drawable、清除桥接，并按 OEM 样式 2 将电池 ImageView 隐藏，直至约数秒后恢复样式 1。
JADX 确认该固件 `StatBatteryMeterView.onConfigurationChanged` 仅调用父类；
`BatteryViewBinder.bind$hideBatteryIcon` 会清空原生 Drawable 并 GONE ImageView。

修复在同一宿主、配置启用、没有失败/隐藏策略、且图标确实已绘制并仍归模块所有时，保留旧帧等待新布局。
新的尺寸仍要 preflight，新帧仍要实际绘制后才能提交 leases。等待期间的必要 resize 不销毁桥接；
原生样式更新仍照常运行。关闭功能、detach、绘制失败、未知样式和第三方替换继续恢复原生。
`HostDecisionTest` 新增插电→样式2→展开→合上→样式1序列及各项拒绝条件。

2026-10-25 Find N5 实测：维护者确认插电瞬间开合不再闪回。模块 ledger 显示
home/configuration 4 次 retained=true，最新 nativeStyle=2、generation=4；home/mount 保持 1 次，
没有 home/native 释放条目；最终 home/style 为 1 retained=true，home/drawn generation=4。
这是当前固件、同一存活宿主的结果；重新创建的宿主仍需首次真实绘制，不能继承旧 View 的 leases。
