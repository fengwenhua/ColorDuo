# 1.1.19 正式发布

- 源码 tag `v1.1.19` → `2848b95112761f4b522a6a071f92c1398ca7564e`；版本 `1.1.19 / 10119`。
- 定向处理系统 Wi-Fi collector 应用可见性后的状态，维持已经绘制的接管；修复原生 Wi-Fi 图标延迟隐藏和后续刷新时重复出现。保留镜像动画与网络类型缓存恢复修复。
- JUnit 187 项、Python 22 项、Lint、Release 构建、签名集成和 Observer 不打包检查通过。新增五项生产方法回归，移除可见性处理后其中三项失败。
- [功能 main CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40811554253)、[功能 tag CI](https://github.com/fengwenhua/ColorDuo/actions/runs/40811554002)、[同签名候选](https://github.com/fengwenhua/ColorDuo/actions/runs/40811578019)、[正式发布](https://github.com/fengwenhua/ColorDuo/actions/runs/40812391977) 均成功。
- 正式 signed-release artifact `12871291201`。固定证书、v2/v3、manifest 签名、包名、版本和源码身份校验通过；私钥仅在受保护的 Actions 环境使用。
- APK SHA-256：`70a98d12e603d14533b2d464aa0a337f7f478a0fd52455f9fc21e0368129b6e9`。正式 APK、checksum、manifest 与已安装候选逐字节相同；独立生成的 ECDSA 清单签名分别验证通过，不要求随机签名字节相同。

## 两仓公开核验

| 仓库 | 版本 | Release ID | 结果 |
| --- | --- | --- | --- |
| fengwenhua/ColorDuo | [v1.1.19](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.19) | 427437406 | 公开、Latest、四附件一致 |
| Xposed-Modules-Repo/io.github.fengwenhua.colorduo | [10119-1.1.19](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10119-1.1.19) | 427437808 | 公开、Latest、四附件一致 |

两仓八个公开下载逐字节一致，标题、说明与源码 tag 匹配。模块仓 README／版本 tag 为元数据提交 `391241e`；源码仓 README 与模块说明同步为 1.1.19。低于 1.1.0（不包含 1.1.0）须先卸载重装，1.1.0 及以上新签名正式版可覆盖升级，说明中保留醒目提醒。

维护者已确认圆环操作后正常。安装前后偏好完全一致；真实 SystemUI PID 17177 在后续回归中保持不变。原始录像、自动切网和静置采样的覆盖与息屏限制见 [Wi-Fi 接管验证](wifi-handoff-2026-10-29.md)。额外胶囊／总开关恢复人工复核尚未收到结果，不计作已验收。

源码仓由 Actions 发布，模块仓通过官方 GitHub 网页适配器上传与发布相同四附件。手机已安装正式版同一 APK，无须再装；网络开关已恢复，圆环、120% 与总开关配置保留。维护者已撤回 1.1.18 的两仓 Release，本轮没有重新创建、修改旧 tag 或清理其他历史版本。
