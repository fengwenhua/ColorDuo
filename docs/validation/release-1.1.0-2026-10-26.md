# 1.1.0 正式发布验收

版本 `1.1.0 / 10100`；源码 tag `v1.1.0` 指向
`4f356b9a53e5bf65a5f00f0e6e21e4d4df0ed423`。
本记录后续仅更新 main 文档，不移动已公开的正式版本 tag。

## 云端与签名

- [正式发布运行 40218450115](https://github.com/fengwenhua/ColorDuo/actions/runs/40218450115)：build、sign、publish 全部成功。
- 同提交普通 CI `40218439847`、tag CI `40218450131` 均成功；包含 27 + 12 个 JUnit 方法、Lint、正常 release 无 Observer 检查及 12 项 Python 测试。
- 在公开 debug fixture 上实际测试 v2+v3、仅 v2、仅 v3、错误固定证书及签名后改包；正式私钥未提供给 build job。
- 默认 API 29 校验会跳过 v2，已改为先验证实际范围，再扩展至 API 24 验证两种签名。
  Build Tools 37 的 `V3.0 Signer:` 标签也已适配；官方 36、37 两版均实跑回归，未通过放宽证书规则处理。
- 正式私钥仍只在 GitHub Environment Secrets 与临时 sign runner 中使用；开发电脑仅下载已签附件、公钥和证书。
- 云端正式 APK 再用本机 Build Tools 37 验证 v2、v3 与固定证书；独立验证更新清单的 P-256 签名、源提交和每个文件摘要。

正式证书 SHA-256：
`47b5960b1d52e3a8b125984b79ab781d6a93dbc7f917def6638d18d3f868542c`。

APK 大小：`3616765` 字节；SHA-256：
`44577bd0ed951a1368badc3dfd8c281c110a953fe07463f3e8a5f44b88173319`。

## 两仓公开附件

- [主仓 v1.1.0](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.0)，Release ID `423564891`。
- [模块仓 10100-1.1.0](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10100-1.1.0)，Release ID `423565118`。
- 两仓均为公开 stable；各有且仅有 APK、APK.sha256、release-manifest.json、release-manifest.sig 四个附件。
  从公开下载链接分别读回全部八个文件，与云端签名产物逐字节比较一致。
- 两份说明均醒目提示先卸载旧版、配置会清除、重新启用 LSPosed 和系统界面作用域。
- 主仓发布由 Actions 完成。模块仓 OAuth 写入受组织限制，未配置专用 token；使用已登录官方 GitHub 网页上传同一组附件并发布，随后从公开下载链接验证。

## 旧 Release 清理

新版本在两仓公开且全部附件验证成功后才清理。主仓使用验证后的发布工具，模块仓使用官方网页的「Delete this release」。

- 主仓删除：`422848914 / v1.0.3`、`422811954 / v1.0.2`、`422754610 / v1.0.0`。
- 模块仓删除：`422850295 / 10006-1.0.3`、`422812518 / 10002-1.0.2`。
- 清理后 API 确认每仓只剩本次 stable Release；两仓 `git ls-remote --tags` 的全部引用与清理前逐字相同。
  未删除源码、commit 或 Git tag。

## 手机状态与覆盖限制

Find N5 在此前验证中完成 312 项 Android 断言、插电立即开合、内外屏三档字体及真实分屏验收，见实施记录与窗口验收文档。
本轮 CI 修复仅涉及 Python/工作流/文档，未改应用功能代码。

手机保留旧测试签名的 1.1.0 调试包与原配置，没有卸载或切换生产签名。
对下载的正式 APK 尝试额外只读 `app_process` 工装时，进程被系统终止（137）；该项不计为 Android 正式包验证通过，未修改系统安全策略。
正式包的真实密码学校验及公开附件回读已通过。原 Android JCA/配置/绘制测试仍以上述已完成的调试工装记录为准，不能当作正式包已装机的证明。

尚无第二台 HyperOS 设备、真实双卡切换样本与 TalkBack 语音验收；保留兼容代码和测试边界，不宣称这些场景已实测。

后续手机已按用户授权完成新签名迁移，并安装同签名的 1.1.1 修复验证包；
见 [暂停充电闪电修复与装机记录](paused-charge-icon-2026-10-26.md)。上述手机状态是正式发布验收时的历史快照。
