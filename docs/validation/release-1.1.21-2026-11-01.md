# 1.1.21 正式发布

- 源码 tag `v1.1.21` → `c2c8c5efde15ee4f199a2cea38567e8477543fbb`；版本 `1.1.21 / 10121`。
- 修复系统「电量百分比」选择「在电池外显示」时，圆环／胶囊内部电量留空和右侧重复数字的问题，对应 [issue #7](https://github.com/fengwenhua/ColorDuo/issues/7)。模块按自身布局显示电量，仅在绘制并接管后隐藏重复百分比，交还系统时恢复原有显示。
- 200 项 JUnit（Release app 58／render 85／host 57）、22 项 Python、Lint、Release 构建、签名集成和 Observer 不打包检查通过。Find N5 两种样式、三种百分比设置及关闭／恢复验收见 [外置百分比记录](outside-percentage-2026-11-01.md)；没有扩展为其他机型已真机验收的声明。
- [同签名候选](https://github.com/fengwenhua/ColorDuo/actions/runs/41266125671)、[README CI](https://github.com/fengwenhua/ColorDuo/actions/runs/41267771149)、[正式发布](https://github.com/fengwenhua/ColorDuo/actions/runs/41267780047) 均成功。
- 正式 signed-release artifact `13067886962`。固定正式证书、v2/v3、独立清单签名、包名、版本及源码身份校验通过；生产私钥仅在受保护的 GitHub Actions 环境使用。
- APK SHA-256：`cce0e24983f4feefe31bc4435ed5f3f0935593c4c88cb1af841d5bf800fab86c`。正式 APK、checksum、manifest 与 `dist/candidate-1.1.21/` 中已安装验收包逐字节相同；两个独立 ECDSA 清单签名分别验证，不要求随机签名字节相同。

## 两仓公开核验

| 仓库 | 版本 | Release ID | 结果 |
| --- | --- | --- | --- |
| fengwenhua/ColorDuo | [v1.1.21](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.21) | 430332390 | 公开、Latest、四附件一致 |
| Xposed-Modules-Repo/io.github.fengwenhua.colorduo | [10121-1.1.21](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10121-1.1.21) | 430332637 | 公开、Latest、四附件一致 |

两仓 APK、APK.sha256、release-manifest.json、release-manifest.sig 共八个公开下载均与同一组正式附件逐字节一致。标题、说明与源码 tag 匹配，发布说明明确关联 issue #7。两仓 README 已同步本版修复，模块仓 README／版本 tag 对应元数据提交 `d9528f7`。

低于 1.1.0（不包含 1.1.0）须先卸载重装，1.1.0 及以上新签名正式版可覆盖升级；README 与两仓发布说明保留醒目提醒。

源码仓由 GitHub Actions 发布；模块仓通过已授权的官方 GitHub 网页适配器上传并公开同一组四附件。本次未操作手机、重新安装或修改配置，没有清理历史 Release 或改写旧 tag；手机上已安装的验收 APK 就是正式版，无须重装。
