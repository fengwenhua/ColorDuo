# 移动信号切换闪现续修

维护者实测：移动数据关闭时开关 Wi-Fi 已正常；Wi-Fi 关闭时重新开启数据，仍会冒出原生信号条；数据开启时开关 Wi-Fi 也有短暂的未知元素。基线为公开 1.1.29。

## 复现与证据

- 设备 Find N5。第一轮固定外屏录像遇到显示切换、第二轮外屏已停用；两轮未作为完整验收。改为读取实际 ON 的物理显示，当前内屏 2248×2480。
- 1.1.29 的有效内屏基线录像在重新开启移动数据后，清楚显示原生四格信号条及活动箭头，圆环同时失去信号点。关闭数据时不出现；重新开启后 mobile 模型变更计数每次增加两次，最后又恢复 inetCondition=1、level=4。
- JADX 确认 OplusMobileSignalExImpl.getCurrentIconId 调用实际 MobileIconSets.getSignalStrength；常规 Binder 返回 MobileIconSets，其 stat_signal_signal_lte_single_0..4 选择不受 inetCondition 和 carrierNetworkChange 影响。
- 旧模块把 inetCondition!=1 的读数直接判为不支持，造成正常图案交还与重取。另外 prepare 的移动行分支在 eligible 已容许现有所有权后，又硬性要求 visibleState==0，排版阶段会不必要地放弃已有读数。

## 修复与边界

- 读取该 model 的实际 toSignalDrawableState 资源。仅对确认普通且等级匹配的 LTE-single 家族允许验证前后的正常信号持续接管；未知警告族、有服务/特殊 SIM/等级数等条件仍严格检查。新方法不可用时使用原保守规则。
- 不因现有所有权的 native overflow/hidden 排版态额外拒绝同一模型；新视图仍须通过现有 eligible 检查。系统 block、实际隐藏/移除、跨窗口、不支持读数继续不接管。
- 网络计划保留移动信号原生内容描述，并加入宿主无障碍描述；计划复制及失去绘制区域时正确保留或清除。日志只记录资源名称与技术状态，不记录内容描述。
- 独立 pure javac 政策检查覆盖普通资源在验证/切换状态下持续显示，以及无服务、特殊 SIM、不同等级、未知图案拒绝。Gradle fixture 抽取真正 prepare/eligible/Plan，覆盖已有信号的三态转换、新视图拒绝、隐藏策略和描述传递。

本地完整门禁、同签名候选及两条真机路径的对照仍在进行，暂不记为修复验收，也未发布。

本地完整门禁通过：241 项 JUnit（app 60、render 107、host 74）、22 项 Python、pure javac 98 项网络政策断言及其他三组、release lint/R8、签名协议和 observer 缺席检查。将旧的 visibleState 额外过滤装回独立 fixture 后，已有信号的 overflow/hidden 保持测试按预期失败，修复版通过。有效基线录像检测到两次重新启用数据期间共 58 个 60Hz 采样帧的原生四格条；模板取自本次实际图案，不复用上轮不完整的扫描范围。
