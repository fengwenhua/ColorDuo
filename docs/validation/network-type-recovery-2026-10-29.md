# 网络类型偶发空白：调查与修复

## 现场与边界

维护者反馈：地铁没有可用 Wi-Fi，Wi-Fi 与移动数据开关均开启且有蜂窝信号，圆环上部空白；手动关闭再开启移动数据后恢复 5G，已遇到两次。

ADB 读取 Find N5（PKH110），系统界面仍为 16.99.12，模块 1.1.16。当前已回到 Wi-Fi 联网状态，模块 dataEnabled=true，日志可见 Wi-Fi 4 级与 5G；没有保存到地铁异常发生瞬间的完整模型状态。旧包上两轮 Wi-Fi 断连／重连未复现持续空白，截图中离开 Wi-Fi 后能显示 5G，不能据此声称已重现地铁现场。

原始 dumpsys、配置、截图与 logcat 仅留在忽略目录 `.local/network-recovery-2026-10-29/`，没有提交用户的网络标识与设备日志。

## 已确定的缓存丢失路径

- JADX MCP 对照当前 ROM 的 `OplusStatusBarMobileViewBinder`、`IconViewBinder`、`OplusMobileIconViewModel` 和 `OplusMobileIconsViewModel.viewModelForSub`。系统以 `getNetworkTypeIcon()` 的 StateFlow 保存当前类型，通过 Icon.Resource 或 null 控制显示。
- bindCustEx 初始化图标后启动 repeatWhenAttached；同一视图重新绑定时，收集器可能在 bind 的 after-hook 前重放当前值。原实现每次 after-hook 都替换 TypeRow，并以 pendingTypes 缓存初始化；该项若已被收集器消费，旧的有效类型会被空值覆盖。
- 相同 StateFlow 值不要求再次变化；仅等待下一次图标回调，缓存可能一直为空，切换移动数据制造新类型变化后才恢复。此路径可由代码时序和回归对照确认，但是否就是本次地铁事件的唯一原因仍需后续实测观察。

## 修复与自动检查

- 同一视图重绑保留已有类型行；类型变化和重绑均核对该订阅原生 ViewModel 的当前 StateFlow 值，读取在内存完成，不做绘制路径 Binder 查询、后台轮询或重启网络。
- 系统 null 会清空过期类型；未知资源保留原生提示。模块自己隐藏的 GONE 不作为“系统没有类型”的依据。可选状态源不兼容时保留回调路径。
- 追加缓存与当前模型资源、逻辑／实际可见性、Wi-Fi 等级和数据开关状态的诊断，方便再次出现时精确定位。
- 回归抽取并执行生产适配器的注册、绑定、回调和采样方法，覆盖重绑重放、漏回调恢复、类型清空、未知资源、订阅源切换与旧回调兼容。
- 将注册阶段替换为旧的“新建行＋消费 pending”行为作对照，3 个回归失败；修复实现的 3 个回归通过。
- JUnit 178 项（app 57／render 77／host 44）、Python 22 项、独立 ColorOS 策略检查、Lint、Release 构建、签名验证集成及 Observer 不打包检查均通过。Lint 0 errors、33 warnings、1 hint。

同签名 1.1.17 测试包与 Find N5 回归待完成；当前公开稳定版仍为 1.1.16，未经发布授权不公开发布本候选。
