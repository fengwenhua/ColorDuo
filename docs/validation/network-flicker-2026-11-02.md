# 网络切换时原生图标闪现

维护者确认是圆环／胶囊保持正常、左侧原生 Wi-Fi／移动信号短暂出现。当前公开基线为 1.1.26，Find N5 已连接；本轮目标是正常网络切换不再露出重复原生图标，不把轮询更快或延时隐藏当作修复。

## 复现与系统来源

保留原配置和 Wi-Fi／移动数据双开状态，在桌面自动开关 Wi-Fi 与移动数据，结束恢复双开。录制外屏物理显示并逐帧裁取状态栏，仅保留本地忽略目录。基线视频约 8.0–8.3 秒可见左侧原生 Wi-Fi 淡入、显示，圆环此时仍为 5G，随后原生消失、圆环恢复 Wi-Fi。不是整个 Drawable 回退。

JADX MCP 对当前 SystemUI APK 的以下源码交叉确认：

- WifiViewBinder 的 collector 会直接把 wifi_group 设为 VISIBLE，未必等待布局。
- WifiIcon.Companion.fromModel 在未验证网络时仍生成 Visible；区别包括无网络的本地化描述。
- OplusWifiSignalExImpl.wifiSignalResId 按 isValidated 取资源数组，当前 WifiIconsExImpl 的普通 stat_signal_wifi_signal_0..4 在两行完全相同。
- ColorDuo 原策略要求 validated=true，因此连接初期即便普通图案完全可表示，也由原生图标显示。新状态的原生隐藏又要等实际 draw 后 main.post 的回调，另有至少一次交接窗口。
- 网络行 detach 原来 releaseAll，会不必要地撤销其他宿主接管。

## 实现

- 通过不可变快照传递实际网络 model、可见图标 resource ID 和本地化无障碍描述。后台回调不遍历 View/WeakHashMap，资源名在主线程解析，不读取 SSID。
- 对本机已确认等价的普通资源族允许验证前参与组件显示，同时将系统网络描述追加到被接管的电池宿主描述。其他无法等价表示的警告族仍交回原生；没有强行吞掉无网络状态。
- 为新网络所有权准备完整的实际像素缓冲，在窗口 pre-draw 校验当前帧／配置／布局／宿主后提交原生隐藏；随后绘制同一缓冲。准备阶段不伪报 hasDrawn/presentation；实际 draw 回调仍确认其他交接。隐藏引发占位变化时取消这次混合遍历，等布局后一起绘制。
- 不在 reconcile 得到新 plan 时提前释放旧所有权；正常 commit 原子交接，失败／关闭／宿主移除仍恢复原生。
- 单个网络 root detach 仅释放关联宿主，不影响其他窗口或状态栏区域。

## 验证进度

基线视频已复现；生产 pre-draw 方法和 Plan／detach 作用域由 Gradle 抽取进真实执行的 host fixtures，覆盖像素准备先于 commit、布局取消与重试、已有所有权重放、无像素／失败／旧计划／关闭／隐藏／替换／尺寸等待，以及无关 QS root 移除。普通未验证图标与未知警告族由政策测试覆盖。

本地最终门禁通过：238 项 JUnit（app 62、render 107、host 69）、22 项 Python、release lint/R8、签名协议集成及 observer 缺席检查。合并入口修复后的 1.1.28 同签名候选、原生缓冲验证和真机视频对照仍在进行，尚未标记问题修复验收，也未发布。

## 同轮补充：外观入口

维护者反馈上版宣传的入口不可见。查明 `slidersForEditor(Appearance)` 返回空表，圆环外观弹层未调用整体尺寸 SliderRow；该 spec 只被 CapsuleContainerRows 复用。将整体尺寸提取为一个共享 spec，在所有样式的外观弹层中紧接 Style 控件显示，不复制存储或限幅逻辑。文案统一为「整体图标大小」。

网络修复 1.1.27 的私有工作流 `41446060046` 尚在构建时，主动取消并合并本补充至 1.1.28；保留不可变源码标签，不改写 1.1.27。1.1.27 未安装、未公开发布。最终候选和验收以 1.1.28 为准。

原版 1.1.26 的 Find N5 外屏 UI XML 已核对：外观弹层含总开关、样式和状态颜色，没有整体尺寸行；这不是仅因标题不一致而找不到。1.1.28 本地完整门禁和 Kotlin 编译通过，后续将用实际 UI XML／拖动核验新入口。
