# ColorOS 16 适配

目标样本：Find N5 PKH110，PKH110_16.0.10.501(CN01)，Android 16 / API 36，
SystemUI 16.99.12。当前实现已在该手机安装并分阶段验证，不代表其他 ROM 的全面兼容。

最新安装版为 `1.1-coloros-activity / 20037`：移动信号已融合时一并隐藏普通上下行箭头，
移除其占位；OEM 更新箭头方向时立即保持隐藏，释放后遵循最新原生状态。
175 项 Android 检查通过，用户确认“三角形已消失，切换正常”。
安装包为 `dist/HyperDuo-1.1-coloros-activity-release.apk`，通用 release 同步更新。
SHA-256：`ca406eb8728b2fe8902f5a44e0e671ae4aceff5cd182df3e1f7d8f45a37ef7bd`。
详见工作区 analysis/coloros16/ACTIVITY_FIX_PROGRESS.md。

上一轮 `1.1-coloros-priority / 20036`：修复 Wi-Fi 连接时网络类型让位、排序后的
网络交接/空占位/抽动，以及快捷编辑卡片不实时更新。用户两项复测通过，Android 检查166项通过。
安装包为 `dist/HyperDuo-1.1-coloros-priority-release.apk`（通用 release 文件也已更新为同包）。
SHA-256：`aed29f3154354b09521d6c62b8b55fbc1559ebc679bcbaf452cb286372517a4f`。
详细原因及实际日志见工作区 analysis/coloros16/PRIORITY_FIX_PROGRESS.md。

已接入电池圆环与自由布局、胶囊尺寸、Wi-Fi/普通单卡信号、已知网络类型、时钟、
共享字体、环外类型/堆叠信号及温度/功率。设置通过原子快照提交，广播校验应用 UID/包名。
仅实际绘制出的内容取得对应原生图标的显示权。停用、配置变化及失效路径释放所有权。

最终构建与安装结果见本文末尾。基础验收已覆盖桌面/锁屏/QS、开关恢复、Wi-Fi 断开重连、
内外屏反复切换、时钟分钟更新、字体导入/清除和 release 重启读取。环外完整组合在 20026
实测内外屏与 QS，SystemUI PID 22858 未因折叠而变化。

## 构建

默认启用当前 ColorOS 适配阶段 `outside`；HyperOS 仍按 ROM 识别进入原 TrioHooks。
只读诊断可指定 `-PcolorOsStage=observe`。正常构建命令：

```sh
gradle :app:assembleDebug :app:assembleRelease --no-daemon --console=plain \
  -PhyperduoVersionName=1.1-coloros-activity -PhyperduoVersionCode=20037
bash tests/coloros-observer.sh
```

libxposed API 102。字体通过官方 remote file 接口只读传递，后台验证摘要与解析，绘制不读文件。
release 无 debug instrumentation，沿用项目原签名，保留现有应用设置与框架作用域。
未修改 LSPosed 数据库、系统 APK、系统安全设置或 boot 文件。

## 当前边界

- 只有普通单卡 Binder 取得接管资格；双卡、特殊 Binder、未知类型、无服务/警告等状态保留原生。
- 原生外置百分比与充电图标继续由系统显示，避免重复绘制对应内部数字/闪电。
- 快充状态取自 OEM 真实充电/技术标志；当前设备满电，真实快充、低电量视觉尚未实测。
- 当前 qsSplit 为未显示的零尺寸宿主；不计该场景通过。无足够布局空间时保留原生对应图标。
- 字体正常导入的已记录共享版本保留最多三代；发布前进程异常退出留下的孤立字体尚不自动清扫。
- 历史 RingSlotsTest 的 empty-layout 断言在原 HEAD 同样失败，未把这项列为已通过。

完整样本、代码审计与阶段记录位于工作区 analysis/coloros16；私有截图留在忽略的 work 目录。

## 20032 验证记录

该阶段 release 为 `1.1-coloros / 20032`，保留文件 `dist/HyperDuo-1.1-coloros-20032-release.apk`，
已更新安装并重新加载 SystemUI，PID 17186。APK 与设备安装文件 SHA-256 相同：

```text
3ab8e9cf5101e8b594a0ca39eb3bdf2d18f5373ec880323b52682633e49842ea
```

debug/release、R8、release lint vital、APK v2 签名、三个 Xposed 入口及 `git diff --check` 通过。
release 无 instrumentation / debuggable。Android 配置、Canvas、布局、字体及占位恢复检查 79 项通过；
纯 Java observer 15、revision gate 31、网络警告策略 22、类型映射 14 项通过。
历史 RingSlotsTest 失败边界见上文，未计入已通过检查。

用户反馈的网速和图标之间空白已修复：原生 mobile_group 内容归零后，status_bar_dot 仍占
65px。ColorOS 会让该 dot 保持 VISIBLE 而不画内容，因此依据父行原生 ICON 状态和
group 实测为空才释放 dot 的占位；原生 overflow、辅助图标出现或模块释放时还原。
未对整个 mobile slot 加忽略规则。真机圆环、胶囊和 QS 的空行宽度均为 0；关闭模块后
原生移动信号行宽度恢复 68px，重新开启恢复接管。截图留在 work/coloros-observer/device。

最终设置 snapshot revision=110：总开关开、胶囊布局 1|3|7|6、信号环内、类型关闭、
堆叠关、读数关、字体为系统默认。测试选择已恢复。完整组合折叠验收来自 20026，
最终 20032 额外核对了占位修复、桌面/QS、圆环/胶囊与总开关，未声称每个版本都重测所有条件。
