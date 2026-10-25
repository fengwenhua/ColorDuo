# 接电但暂停充电时的原生闪电

## 复现与原因

Find N5 换装 1.1.0 新签名正式版并恢复原设置后，圆环右侧出现系统闪电。
真机显示 USB 已连接、电量 92%、状态 NOT_CHARGING；模块读取到
`ready=true charging=false quick=false plugged=true full=false`。
圆环正常绘制，原生 `battery_charge_icon` 可见，`chargeIcon owned=false nativeVisibility=0`。
状态栏截图与日志一致。

旧的 `commitCharge` 仅在 `frame.power.charging` 为 true 时接管普通原生充电图标。
ColorOS 暂停充电时仍可能显示这个图标，因此它在退出充电状态后被归还。
JADX 核对了 BatteryViewBinder 的普通充电图标更新方法；反向充电使用独立视图/更新路径。

## 修复与边界

`HostDecision.ChargePresentation` 将普通图标接管条件集中为：
用户布局已经呈现、绘制帧仍匹配当前电源状态、电源状态已知，并且正在充电或仍然接电。
接电暂停及满电因此继续接管普通原生闪电，不修改 `charging`、`quick`、充电布局或快充布局的选择。
禁用、退出接管、未知电源状态及过期绘制帧仍正常归还；不操作反向充电视图。

修复提交：`e778392`。
新增一组决策单测，覆盖暂停/正在充电/拔下电源/非用户布局/过期帧/未知电源；
Android debug 工装补两项暂停后更新的 lease 断言（该新增 debug 工装本轮未安装运行）。
本地完整 release 构建、28 个 app 单测、12 个 render 单测及 Lint 通过；Python 12 项通过。

## 同签名装机验收

- 候选源码 tag `v1.1.1`，提交 `5fa84a224e138a2d42a930460952b99a94adeb94`。
- [运行 40219862551](https://github.com/fengwenhua/ColorDuo/actions/runs/40219862551) 的 build/sign 成功；
  设置 `publish_release=false`，publish 跳过，没有创建或清理公开 Release。
- 正式私钥仍只在受保护云端任务使用。下载四个 signed-release 附件，核对更新清单签名、源提交、摘要及固定 APK 证书。
- `1.1.1 / 10101` APK SHA-256：`16863bf0923c3ba98f4a321df6a3891ca943fec4a13f566cfb2f8cbcb312968f`。
- 通过 `adb install -r` 覆盖生产签名的 1.1.0，未再次卸载，读取安装元数据确认非 debuggable。
  更新前后用户设置逐项一致；仅传输 epoch/revision/snapshot 等内部字段排除比较。
- 重载 SystemUI（12509 → 24459）。同一真实暂停充电状态下，主屏圆环正常绘制，
  `chargeIcon owned=true nativeVisibility=0`，普通原生图标变为 GONE；状态栏截图确认右侧闪电及占位消失。
- 维护者随后确认「已消失，切换和开关正常」。再次读取日志，主屏继续绘制且接管有效，未出现适配器故障。
  不将隐藏场景 host 的 `owned=false` 误记为可见主屏退出接管。

## 新签名迁移记录

此次先将旧测试签名 1.1.0 的当前设置及原 APK 备份到本机被忽略的私有目录，
用户授权后执行卸载、安装公开的新签名 1.1.0。
恢复 23 个用户设置项；4 个内部传输/字体历史字段由新安装重新生成，未恢复旧 profileinstaller 标记。
配置没有自定义字体文件。公开 APK 从安装路径重新拉回，逐字节匹配发布附件，并验证生产证书。
之后才安装上述同签名 1.1.1 修复包。当前手机已使用新签名，后续相同签名版本可覆盖安装。

此记录完成时公开 stable 仍为 1.1.0；1.1.1 为已装机验证的云端签名产物。

随后已按维护者要求公开发布 1.1.1，见 [正式发布验收](release-1.1.1-2026-10-26.md)。
