# 构建输入校验

使用 JDK 21、Android SDK 37，以及仓库内 `./gradlew`（Windows 为 `gradlew.bat`）。
Wrapper 固定 Gradle 9.8.0 发行地址及 SHA-256；`scripts/check-wrapper.py` 另校验 bootstrap JAR。
摘要来自官方 [发行包](https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256)
与 [Wrapper JAR](https://downloads.gradle.org/distributions/gradle-9.8.0-wrapper.jar.sha256) 端点。
CI 与受保护发布任务均先检查 Wrapper，再执行相同构建入口。

`gradle/verification-metadata.xml` 固定本轮解析的 448 个组件、714 个 artifact 摘要，
同时校验模块元数据；包含仓库内 vendored libxposed。未配置 trusted-artifacts 或全局忽略规则。
Linux/Windows AAPT2 工具包单独从 Google Maven 获取并计算摘要，macOS 包也与远端重新核对。
正常构建使用 strict 验证；未知或更改的依赖会失败。

升级依赖时单独提交版本变更，核对 Google Maven、Maven Central 或 Gradle Plugin Portal 的来源及差异，
再用 `./gradlew --write-verification-metadata sha256` 生成待审清单。
首次生成的 checksum 是固定构建输入，不能代替依赖代码审计；不要在日常 CI 自动重写它来接受任意新字节。
依赖校验机制见 [Gradle 官方文档](https://docs.gradle.org/current/userguide/dependency_verification.html)。

本轮验证：严格模式下单元测试、Lint、debug/release 构建成功；故意把 Kotlin Gradle 插件摘要改为零后，
`help` 在依赖解析阶段失败。随后逐字节恢复元数据，正常 release 构建再次通过。
首次 Linux CI 暴露缓存中未重放的父 POM/BOM 元数据；使用全新的依赖缓存重新生成后，
新增 7 个描述文件，均从 Maven Central 独立下载核对。原有 707 份摘要未变化，没有升级依赖或关闭校验。
源码统一 LF，PowerShell/批处理维持 `.gitattributes` 的 CRLF。
