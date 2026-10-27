# 1.1.16 正式发布

- 源码 tag：`v1.1.16` → `b8fb97515bd9339cb0357d2457ba1fa1a787e1a1`；版本 `1.1.16 / 10116`。
- 新增首页状态栏高度调节（100%–150%）与外观页胶囊宽度实时调节，修复高度恢复、右侧越界、下拉缩放抖动和胶囊满电重叠。
- Find N5 内外屏、缩放恢复、组件样式和满电宽度调整已分阶段验收；JUnit 175 项、Python 22 项、Lint 和 Release 构建通过。
- [main 功能 CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40516107537)、[tag CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40516106939)、[正式发布](https://github.com/fengwenhua/ColorDuo/actions/runs/40518668002) 均成功。
- 生产私钥仅在受保护的 Actions 环境使用。正式签名 artifact `12740378285`，APK 证书固定值、v2/v3、manifest 签名、版本与源码身份均校验通过。
- APK SHA-256：`bed532409af089375dd1dc0edfa5f5bd9083f91afcda488f3a54d67f6d81289f`。正式 APK 与已安装验收包逐字节一致，无须再安装。

## 两仓公开核验

| 仓库 | 发布 | Release ID | 状态 |
| --- | --- | --- | --- |
| 源码仓 | [v1.1.16](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.16) | 425552453 | 公开、Latest、4 附件一致 |
| 模块仓 | [10116-1.1.16](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10116-1.1.16) | 425554513 | 公开、Latest、4 附件一致 |

两仓 APK、APK.sha256、release-manifest.json、release-manifest.sig 共八个公开下载均与同一组正式附件逐字节一致，标题与说明匹配源码 tag。正式清单的 ECDSA 签名独立生成并验证，不要求与候选签名的随机字节一致。

源码仓由 Actions 发布；模块仓 README 和版本 tag 已推送，经官方 GitHub 网页适配器上传并公开相同附件。模块仓元数据提交 `de839d9`；主仓 README／module-README 已同步 1.1.16。

低于 1.1.0 的版本（不包含 1.1.0）需先卸载重装；1.1.0 及以上新签名正式版可覆盖升级。两仓 README 和发布说明均保留提醒。未清理历史 Release，未改写旧 tag，未操作手机设置。
