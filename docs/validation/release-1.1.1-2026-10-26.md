# 1.1.1 正式发布验收

- 版本 `1.1.1 / 10101`，正式源码 tag `v1.1.1` 指向 `945685e705062ec31573049b297ec6e0815a26c7`。
- [Actions 40220708914](https://github.com/fengwenhua/ColorDuo/actions/runs/40220708914) 的 build、sign、publish 全部成功。
  应用代码与此前已装机确认的修复候选一致；本轮仅补充发布说明、README 与验收文档。
- 顶部醒目提示：**低于 1.1.0 的版本（不包含 1.1.0）必须卸载重装；1.1.0 新签名正式版可覆盖升级并保留配置。**
  旧测试签名调试包的签名差异另行说明，避免仅凭相同版本号误判可覆盖。
- 主仓 README、模块仓 README 与两份 Release 说明均已同步上述边界。

## 公开下载与同源校验

- [主仓 v1.1.1](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.1)，Release ID `423579483`。
- [模块仓 10101-1.1.1](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10101-1.1.1)。
- 两仓均已公开并标记 Latest；各有 APK、APK.sha256、release-manifest.json、release-manifest.sig 四个附件。
- 全部八个附件从公开下载链接匿名读取，与云端正式签名产物逐字节一致。
- 更新清单 P-256 签名、APK 的固定证书与 v2/v3 均经本机复核。
- APK `3616765` 字节，SHA-256：`595d1d8294f65061d30aea736ac0cb3da5a305480979e543e0a70ddd9d8a45f2`。
- 旧候选与正式 APK 的 DEX、资源、assets 等应用内容均一致。
  唯一差异是 AGP 记录的 Git revision 从 `5fa84a2` 更新至 `945685e`，以及由此改变的标准 V1 签名元数据。
  没有把不同来源的包与清单混用，也没有移动已公开的正式 tag。

模块仓通过官方 GitHub 网页发布同一组附件。匿名主仓 API 在同步时触发限流，
网页适配器增加可选环境凭据读取固定主仓元信息；公开附件下载不带认证头，跨域移除认证头。
新增两项边界测试，Python 测试共 14 项通过。未将组织 OAuth 限制作为可绕过的权限。

本次保留 1.1.0 Release，不执行旧 Release 或 Git tag 清理。
手机仍是已验证的 1.1.1 同签名候选，其应用内容与正式包一致；本次发布没有再次安装或重载手机。
