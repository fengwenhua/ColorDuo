# 1.1.20 正式发布

- 源码 tag `v1.1.20` → `223cd6f8885dde44d48c8e6be5ac528d74939497`；版本 `1.1.20 / 10120`。
- 修复 Wi-Fi 保持开启但已断连时，过期的“流量关闭”缓存令圆环／胶囊网络类型持续留空。实时同步默认数据 SIM 的移动数据开关，并处理 SIM 初始化、切换及旧回调；真正关闭流量时仍隐藏类型。
- JUnit 195 项、Python 22 项、Lint、Release 构建、签名集成、Observer 不打包检查通过；准确断连现场、三轮数据开关及维护者显示确认见 [验证记录](wifi-loss-data-state-2026-10-30.md)。
- [功能 main CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40990482717)、[功能 tag CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40990482312)、[同签名候选](https://github.com/fengwenhua/ColorDuo/actions/runs/40990543169)、[README CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40991938159)、[正式发布](https://github.com/fengwenhua/ColorDuo/actions/runs/40991953687) 均成功。
- 正式 artifact `12949178455`。固定正式证书、v2/v3、签名清单、包名、版本及源码身份通过校验，生产私钥只在受保护的 Actions 环境使用。
- APK SHA-256：`d1628b146cd367f2add07d5aa2836ef83cf52e55a56a61a4bd2e7ecc57e88abf`。正式 APK、checksum、manifest 与已安装候选逐字节相同；独立 ECDSA 清单签名分别验证有效，不要求随机签名字节与候选相同。

## 两仓公开核验

| 仓库 | 版本 | Release ID | 结果 |
| --- | --- | --- | --- |
| fengwenhua/ColorDuo | [v1.1.20](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.20) | 428594880 | 公开、Latest、四附件一致 |
| Xposed-Modules-Repo/io.github.fengwenhua.colorduo | [10120-1.1.20](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10120-1.1.20) | 428595407 | 公开、Latest、四附件一致 |

两仓 APK、APK.sha256、release-manifest.json、release-manifest.sig 共八个公开下载均与正式附件逐字节相同，标题和说明与源码 tag 一致。模块仓 README／版本 tag 对应元数据提交 `0b3c134`，两仓 README 已同步本次修复。

发布说明醒目保留：低于 1.1.0（不包含 1.1.0）须先卸载重装，1.1.0 及以上新签名正式版可覆盖升级。

源码仓由 GitHub Actions 发布；模块仓通过已授权的官方 GitHub 网页适配器上传和公开同一组四附件。未清理历史 Release、改写旧 tag、重新安装手机或操作手机配置。手机已安装相同正式 APK，无须重装；最后验收状态及限制保留在验证记录中。
