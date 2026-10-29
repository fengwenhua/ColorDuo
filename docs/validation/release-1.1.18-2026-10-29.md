# 1.1.18 正式发布

> 后续状态：维护者因原生 Wi-Fi 图标重复出现撤回了 1.1.18 的两仓 Release。以下保留当时的发布证据；镜像动画与网络类型修复现随 [1.1.19](release-1.1.19-2026-10-29.md) 一起发布，当前稳定版以 1.1.19 为准。

- 源码 tag：`v1.1.18` → `325f2e1b1e7748287f4402555af32adf6c94aad9`；版本 `1.1.18 / 10118`。
- 修复自定义状态栏下拉／收回时遗漏缩放的镜像交接，保持原生过渡动画；同时包含 1.1.17 候选中验收的网络类型缓存恢复修复，1.1.17 未单独公开发布。
- JUnit 182 项、Python 22 项、Lint 与 Release 构建通过；维护者确认原始高度与自定义高度的动画正常，圆环／胶囊、切网与双关恢复已分阶段验收。
- [功能 main CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40806831435)、[功能 tag CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40806831363)、[正式发布 40808394729](https://github.com/fengwenhua/ColorDuo/actions/runs/40808394729) 全部成功。
- 正式 signed-release artifact `12869446753`；生产私钥继续仅在受保护的 Actions 环境使用。APK 证书、v2/v3、manifest 签名、包名、版本和源码身份校验通过。
- APK SHA-256：`fbc22a7be3d7c5ee4b2cc0ab0385d1b71c357cfaf426045fc3b7d62c7ef70d5e`。正式 APK、checksum 与 manifest 均与已安装验收候选逐字节一致，无须重装；重新生成的 ECDSA 清单签名独立验证有效，不要求随机签名字节与候选相同。

## 两仓公开验证

| 仓库 | 版本 | Release ID | 结果 |
| --- | --- | --- | --- |
| fengwenhua/ColorDuo | [v1.1.18](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.18) | 427412044 | 公开、Latest、4 附件一致 |
| Xposed-Modules-Repo/io.github.fengwenhua.colorduo | [10118-1.1.18](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10118-1.1.18) | 427412769 | 公开、Latest、4 附件一致 |

两仓 APK、APK.sha256、release-manifest.json、release-manifest.sig 共八个公开下载均与同一组正式附件逐字节一致，标题及说明与源码 tag 完全匹配。两仓 README 已同步新版本内容，模块仓 README 与版本 tag 对应元数据提交 `7229f7f`。

低于 1.1.0（不包含 1.1.0）的版本须先卸载重装；1.1.0 及以上新签名正式版可覆盖升级。README 与两仓发布说明均保留提醒。

源码仓由 Actions 发布；模块仓通过官方 GitHub 网页适配器上传及公开相同四个附件。没有清理历史 Release、改写旧 tag、重新安装手机或修改用户配置。最后验收状态仍为外屏、圆环、120%、总开关开启。
