# GitHub Actions 正式发布

日常 CI 执行测试、Lint 和 unsigned release 构建，没有生产私钥，也不会创建 Release。
正式发布入口为 `.github/workflows/release.yml`，从 main 手动启动并传入已提交的版本 tag。

## 发布输入

1. 更新 `version.properties` 和 `docs/release-notes/release-notes-版本.md`，完成各项检查并提交、推送 main。
2. 创建并推送对应的 `v版本` tag；tag 必须指向 main 历史内的干净提交，版本号和版本构建号需一致且递增。
3. 在 Actions 的 release 页面传入 tag。`publish_release` 默认 true；`prune_old_releases` 默认 false，只有明确需要清理旧版时才勾选。
4. 审核 `release` 环境任务。build job 不接触正式密钥；sign job 只签该次构建的 unsigned APK，生成清单/签名；
   publish job 回读四个远端附件校验后才将 draft 发布。

```sh
gh workflow run release.yml --ref main -f tag=v1.1.0 -f prune_old_releases=false
```

四个附件为 APK、APK.sha256、release-manifest.json、release-manifest.sig。两仓必须上传同一组附件。
同 tag 重试只接受一致的标题、说明和附件，不覆盖已发布的不同内容，不删除同名 APK 后重传。

需要在已安装生产签名的手机上测试修复时，可设置 `publish_release=false`：
仍通过完整 build 门禁和受保护 sign job，仅生成 `signed-release` artifact，跳过发布、同步与清理。
从该 artifact 核验签名后安装，避免为了测试再换回公开 debug 签名或卸载用户配置。

```sh
gh workflow run release.yml --ref main -f tag=v1.1.1 -f publish_release=false -f prune_old_releases=false
```

## 模块仓权限

GitHub 自带 `GITHUB_TOKEN` 仅能写本源码仓。自动同步模块仓需要环境 Secret `MODULE_REPO_TOKEN`，
其凭据须由 Xposed-Modules-Repo 组织授权，且只允许写 `io.github.fengwenhua.colorduo`。
当前已知 OAuth 组织限制不能靠复制主仓 token 绕过；没有该 Secret 时工作流会明确报告模块仓待同步，
并推迟全部历史清理。

有合适凭据时，在 tag 对应的干净源码目录下载 signed-release artifact，使用相同发布器补齐：

```sh
python3 scripts/github-release.py publish --repo Xposed-Modules-Repo/io.github.fengwenhua.colorduo --tag 10100-1.1.0 --dist dist
```

若组织仍只允许网页发布，则使用已经登录的官方 GitHub 网页上传这四个同源附件；对照本地及两仓公开下载的 SHA-256，
确认公开发布完成。该回退只解决组织授权差异，APK 仍必须取自受保护的 Actions 签名产物。

macOS 可使用受跟踪的网页适配器（需要 Chrome 已登录且允许 Apple Events 执行 JavaScript）：

```sh
python3 scripts/module-browser-upload.py inspect
python3 scripts/module-browser-upload.py upload --dist dist
python3 scripts/module-browser-upload.py publish --dist dist
```

先把模块仓 README 与版本 tag 推好，打开其 `releases/new?tag=10100-1.1.0` 页面。
脚本只允许这个模块仓，先验证本地签名清单、源码提交与主仓公开附件字节，再分两步上传 draft、发布；
不会读取浏览器凭据或 CSRF 值。中断后先 inspect，拒绝重复上传已有附件。发布后仍须回读模块仓公开附件。

## 显式清理历史版本

清理器先核对新稳定版及其签名产物，再列出精确的旧 Release ID/tag；默认只预览，显式 `--execute` 才删除。
不删除任何 Git tag、提交、分支或源码，draft 也不作为“已发布旧版本”处理。

```sh
python3 scripts/github-release.py prune --tag v1.1.0 --dist dist
python3 scripts/github-release.py prune --tag v1.1.0 --dist dist --execute
python3 scripts/github-release.py prune --repo Xposed-Modules-Repo/io.github.fengwenhua.colorduo --tag 10100-1.1.0 --dist dist --execute
```

本轮维护者已明确授权：1.1.0 换签版验证并发布后，清理两仓此前发布的全部旧 Release。
执行前仍须核对具体目标，执行后记录剩余版本。后续发版的默认行为仍是保留历史。
