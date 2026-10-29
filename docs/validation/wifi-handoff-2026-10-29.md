# Wi-Fi 原生图标重复出现

## 现场与修复边界

Find N5、ColorOS 16、SystemUI 16.99.12，1.1.18、外屏 120%。维护者报告开关 Wi-Fi 后原生图标在圆环左侧延迟消失，并可能在稍后再次出现。ADB 录像已记录左侧原生 Wi-Fi 与圆环内 5G 同时出现的现场，切换网络后恢复。原始日志、录像、抽帧与反编译内容仅存忽略目录 `.local/wifi-handoff-2026-10-29/`。

JADX 对照 WifiViewBinder、ModernStatusBarView、ModernStatusBarViewVisibilityHelper、StatusIconContainer：原来只监听上游 Wi-Fi 模型计算和布局变化，上游回调可能早于逐个视图的可见性应用；相同尺寸的后续写入不能保证触发新的接管。原来同一接管对象的 commit 也没有重新确认 Wi-Fi 隐藏状态。

- 定向监听已验证 ROM 的 Wi-Fi 图标／可见性 collector，监听实际写入后的状态，即便模型值和尺寸未变也安排一次合并刷新。
- 仅对已经实际绘制、仍由可见三合一控件持有的 Wi-Fi 接管，立即维持 ignored slot 和 HIDDEN 状态。
- 针对可见性迟到写入，修正 HIDDEN 下的原生 group／dot。原生 setVisibleState 在数值不变时直接返回，单独再次调用它不能修正这种写入。
- 没有已绘制接管、不再可见、关闭总开关或能力探测失败时保留／恢复原生图标。保留未验证网络等原有原生回退策略，不新增轮询或固定延时隐藏。
- 增加脱敏的 Wi-Fi 模型与接管状态诊断，不记录 SSID 等网络身份信息。

## 自动验证

新增五项直接提取生产方法的主机回归：迟到可见性写入、20 次重复刷新与 slot 清除、先应用原生后自定义绘制的交接、释放／不可见后的原生恢复、未知视图与能力失败回退。

完整 JUnit 187 项（Release app 57、render 81、host 49）及 Python 22 项通过；Release 构建、Observer 不打包检查通过，Lint 0 errors、34 warnings、1 hint。负向对照移除实际可见性应用回调处理后，五项新增测试中三项失败；恢复生产修复后全部通过。

同签名候选、真机回归及发布结果待后续补充；本记录不代表所有系统场景已验收。
