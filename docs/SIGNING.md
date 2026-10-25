# 正式签名和更新信任

正式签名私钥及更新清单私钥只在 GitHub `release` 环境的 Secret 中保存，不在源码、开发电脑或 artifact 中保存。
2026-10-25 由受审的 `initialize signing secrets` 运行 `40204334588` 在临时 GitHub runner 生成；
公钥和 APK 证书通过只包含三个公共文件的 artifact 导出并本地核对。

APK 证书 SHA-256：`47b5960b1d52e3a8b125984b79ab781d6a93dbc7f917def6638d18d3f868542c`。
证书原文在 `.github/trust/release-certificate.pem`；更新公钥在 `app/src/main/assets/update-signing-public.pem`。

环境 Secret：

- `RELEASE_KEYSTORE_BASE64`：PKCS12 keystore；alias 为 `colorduo`。
- `RELEASE_STORE_PASSWORD`：keystore/key 密码。
- `UPDATE_SIGNING_KEY_PEM`：独立 P-256 更新清单私钥。

一次性 `SIGNING_SETUP_TOKEN` 已在初始化成功后删除，后续正式发布不依赖它。初始化工作流拒绝覆盖上述已有 Secret。
环境要求 fengwenhua 审核，限制 main 分支和 v* tag；正式发布流程还须校验 tag 提交属于 main。
Secret 不可作为唯一可恢复备份：维护者应另选云密码库保存加密恢复副本，当前尚未配置独立备份服务。

## 签名迁移

新正式证书与 1.0.3 及以前的公开测试证书独立，不建立旧证书 lineage。用户须先卸载旧版，再安装新正式版，
设置会清除；安装后须在 LSPosed 重新启用 ColorDuo，勾选系统界面作用域并重载系统界面。
之后使用同一新证书的正式版本可覆盖更新。历史源码/tag 保留，旧 Release 在新版本发布后按本次授权清理。

debug 继续使用公开测试证书，便于历史验证包覆盖调试；release 构建始终 unsigned，只有受保护的 Actions job 可以正式签名。
不要把 unsigned/debug APK 上传到正式 Release。也不要在已装新证书的用户手机上直接覆盖安装 debug 包。

## 应用内更新验证

每版须发布 `release-manifest.json` 和原始 DER `release-manifest.sig`，对 JSON 原始字节做 SHA256withECDSA 签名。
算法选择兼容 Android API 29+：平台 Ed25519 验证从 API 33 才提供，未引入额外加密库。

清单须包括 schema=1、packageName、versionName、versionCode、apkName、apkSize、sha256、certificateSha256。
应用先验证清单签名，再验证固定包名、版本/tag、文件名、签名证书和大小范围。下载优先官方源，代理只传输
同一组受签名保护的数据。下载后及安装前复核 APK SHA-256、真实包名/版本和当前签名，拒绝降级、错误包和 debuggable APK。
HTTP Content-Length、同代理返回的 SHA 文本、GitHub release notes 均不能代替独立签名。

验证包含 JVM 篡改/错误密钥/非法身份/文件截断用例及 Android 实际 JCA/JSON 探针。
Release APK 的真实签名与下载回读由发布工作流继续校验。

## apksigner 校验范围

本项目 minSdk=29。默认 `apksigner verify` 找到 v3 后会跳过 v2，
所以输出 `v2: false` 并不表示 APK 没有 v2 签名。正式脚本先按实际设备范围验证，
再用 `verify --min-sdk-version 24` 要求 v2、v3 均验证通过；两次均须恰好一个签名者且匹配固定证书。
这只扩大检查范围，不修改 APK 的 minSdk，也不放宽证书检查。
依据：[Android ApkVerifier 的签名选择逻辑](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/src/main/java/com/android/apksig/ApkVerifier.java)。

发布运行 `40217235276` 在此检查误判后停止，publish 未执行。修复增加 6 组解析单测，
并在普通 CI/发布 build 阶段使用公开 debug fixture 实际签署临时 APK：双签名通过，
缺少任一签名、错误证书和修改后的 APK 均被拒绝。测试产物只在临时目录中存在，正式发布仍仅使用云端私钥。

Build Tools 37 将证书显示前缀从 `Signer #1` 改为 `V3.0 Signer:`。
校验器按证书摘要字段读取，仍要求唯一签名者、唯一合法证书，并与固定摘要比较；
不会误取公钥摘要。修复后用官方 Build Tools 36 和 37 分别运行完整临时签名回归。
