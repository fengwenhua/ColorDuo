# 1.1.25 正式发布：圆环开口与可选数字让位

维护者授权在 CI／测试通过后直接发版，并在发布后回复 issue #8。两仓均已公开为 Latest：

- [源码仓 v1.1.25](https://github.com/fengwenhua/ColorDuo/releases/tag/v1.1.25)，Release `430418090`。
- [LSPosed 模块仓 10125-1.1.25](https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.colorduo/releases/tag/10125-1.1.25)，Release `430420471`。
- [issue #8 回复](https://github.com/fengwenhua/ColorDuo/issues/8#issuecomment-6554876125)：说明开口随字号／字重和闪电大小调整，附新版链接请反馈者复测。只添加回复，没有主动关闭 issue。

源码 tag `v1.1.25` 保持指向 `c05783f3376653c4a1bfddae211657aa270032d8`；模块仓 metadata main／tag 为 `4e898f2`。两仓发布说明包含 issue #8 与两种放大方式，并醒目保留：**低于 1.1.0（不包含 1.1.0）先卸载重装，1.1.0 及以上新签名正式版可覆盖升级**。没有重发已撤回的 1.1.18，没有清理历史 Release。

## 验证与附件

- [main CI 41281431105](https://github.com/fengwenhua/ColorDuo/actions/runs/41281431105)、[tag CI 41281431120](https://github.com/fengwenhua/ColorDuo/actions/runs/41281431120)、[候选 41281488970](https://github.com/fengwenhua/ColorDuo/actions/runs/41281488970)、[正式发布 41283260750](https://github.com/fengwenhua/ColorDuo/actions/runs/41283260750) 均成功；发布准备文档 main CI `41283236292` 通过。
- 217 项 JUnit（release app 58／render 102／host 57）、22 项 Python、Lint、Release、签名集成和 Observer 不打包检查通过。旧高度限制的负向对照使两项有效字号回归失败，恢复后通过。
- 正式 artifact `13074083140`，APK `ColorDuo-1.1.25.apk`，大小 `3,706,877` 字节。
- APK SHA-256：`e0c6a287900e9c8f418554086abdfd356397a9f1db6eb2dd56fcf781e70d103b`。
- 正式 APK／checksum／manifest 与已安装候选逐字节相同，两份 manifest 签名均验证通过；APK 固定证书 `47b5960b1d52e3a8b125984b79ab781d6a93dbc7f917def6638d18d3f868542c`、v2/v3 通过。
- 两仓八个公开附件下载回读均与正式 artifact 相同；源码身份、版本、发布正文及 Latest 均核对。Chrome 回退发布只操作本次 tag 编辑器与已验证的四个文件，未覆盖其他草稿。

附件和核验结果在忽略目录 `.local/release-1.1.25-formal/`、`.local/release-1.1.25-2026-11-01/`，候选备份在 `dist/candidate-1.1.25/`。

## 真机边界

Find N5 已安装同一 APK，全部偏好保留；SystemUI 重载后网络／布局正常，120% 高度保持。设备随后锁屏，没有把这轮下拉选择器的人工触控计为完成。同机原生 Bitmap／Canvas 加载并校验已安装 APK，验证保持上限 37、让位 30／36／44／56 实际生效、九种 Wi-Fi／线宽组合的数字／中部／信号点间距，以及切回保持的恢复。临时工具、远端 APK 和一次性测试密钥已清理，测试前后模块偏好完全一致，见 [放大验证](upper-value-size-2026-11-01.md)。

公开 APK 与手机已装包相同，无须重装。新选项默认保持，未替维护者改成让位或提高字号。其他字体／机型、本轮折叠／旋转及下拉选择器人工操作没有逐一验收。原优化清单剩余项不变。
