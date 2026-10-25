# ColorDuo 文档索引

ColorDuo 基于 HyperDuo，保留原作者署名、许可证和历史。当前实现与验证以本目录当前文档为准；
旧版本工作日志、包名与 HyperOS 设备记录不会被当作 ColorOS 的实测结果。

| 文档 | 用途 |
| --- | --- |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发入口 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 当前架构与职责 |
| [CONFIGURATION.md](CONFIGURATION.md) | 配置编辑、传输、Schema 和迁移 |
| [HOST-STATE.md](HOST-STATE.md) | 宿主接管、失败恢复、充电折叠交接 |
| [COMPATIBILITY.md](COMPATIBILITY.md) | 兼容性范围、能力画像、复制报告 |
| [TESTING.md](TESTING.md) | 本地/CI 门禁与真机验证 |
| [RELEASING.md](RELEASING.md) | GitHub Actions 发布、模块仓同步、显式清理 |
| [SIGNING.md](SIGNING.md) | 正式签名、密钥托管和更新信任链 |
| [COLOROS_ADAPTATION.md](COLOROS_ADAPTATION.md) | ColorOS 适配背景及阶段验证 |
| [COLOROS_OBSERVER.md](COLOROS_OBSERVER.md) | 诊断构建 |
| [RELEASE-NOTES-STYLE.md](RELEASE-NOTES-STYLE.md) | 用户可读发布说明规则 |
| [validation/optimization-progress.md](validation/optimization-progress.md) | 本轮实施与验收 |

`release-notes/` 保存已提交的发布说明；删除 GitHub Release 不会删除这些源文件。
`validation/` 保存每轮具体证据；`history/` 保存历史开发记录；`images/` 保存 README 素材。
根目录 `README.md` 面向用户，`module-README.md` 是模块仓首页源文件。

上游历史文档仍保留在 [开发记录归档](history/DEVELOPMENT-upstream-2026-10-23.md)、
[开关复盘](SWITCHES-REVIEW.md)、[胶囊接入](CAPSULE-INTEGRATION.md)及日期命名的 issue/验证记录中。
