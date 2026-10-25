# ColorDuo 开发入口

当前实现按以下文档维护：

- [架构与职责](ARCHITECTURE.md)：ROM 路由、设置页与绘制编辑器的文件边界。
- [配置协议](CONFIGURATION.md)：即时预览、合并发布、Schema 和历史迁移。
- [宿主状态与恢复](HOST-STATE.md)：先绘制后接管、充电与折叠交接。
- [测试](TESTING.md)：Gradle 门禁、真实 Android Canvas 与 Find N5 工装。
- [发布](RELEASING.md)与[签名](SIGNING.md)：受保护 Actions、新证书、四附件校验和显式清理。
- [兼容性画像](COMPATIBILITY.md)：真实能力报告与已验证范围。
- [优化实施记录](validation/optimization-progress.md)：每项实现、验证和限制。

本地构建使用 JDK 21、Android SDK 37 和仓库内 `./gradlew`。
普通 release 产物不签正式证书；正式签名只在受保护的 GitHub `release` 环境运行。
依赖更新单独审阅验证元数据，不能为绕过校验而删除/忽略 checksum。

原作者约 3000 行架构演进与逐轮日志完整保存在
[上游开发记录](history/DEVELOPMENT-upstream-2026-10-23.md)，其中历史版本、包名、工具链及发布方式
对应当时实现，不能用作当前 ColorDuo 发版命令。后续逐轮记录放入 `history/` 或 `validation/`。
