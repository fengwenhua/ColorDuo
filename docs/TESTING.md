# 当前测试入口

普通提交执行：

```sh
./gradlew :app:testReleaseUnitTest :render-tests:test :app:lintRelease :app:assembleRelease
python3 scripts/check-observer.py app/build/outputs/apk/release/app-release-unsigned.apk --expect absent
```

`app/src/test` 用 JUnit 运行路由、观察记录上限、快照时序、网络警告保护、精确类型标签以及插电事件序列。
既有 82 项断言原样迁入；`bash tests/coloros-observer.sh` 仍可作为不依赖 Android SDK 的快速入口。
AGP 9.4 的 release host tests 在 `androidComponents.beforeVariants` 显式启用，避免 CI 因任务未生成而跳过。

`render-tests` 是纯 JVM 测试项目，直接编译真实 Prefs/Settings/ChargeLayout/ConfigPacket/Renderer 等源码，
Android 替身与 Java2D 只在测试编译范围内，不进入 APK。覆盖普通/快充布局隔离、Bundle/JSON 传输、
非法快照拒绝、首次安装与已有配置保护，以及两种宿主比例下的确定性绘制。Java2D 通过不代表 Android GPU/OEM Hook 已验证。

真机验证（安装/重载均为显式参数，默认只运行当前 debug 包探针）：

```sh
python3 scripts/verify-device.py --serial DEVICE_SERIAL --output .local/verification.json
python3 scripts/verify-device.py --serial DEVICE_SERIAL --apk app/build/outputs/apk/debug/app-debug.apk --restart-systemui --output .local/verification-reload.json
python3 scripts/verify-device.py --serial DEVICE_SERIAL --skip-probes --output .local/verification-release.json
```

可传 `--adb` 指定绝对路径。脚本拒绝降级/卸载，不清空 logcat，不输出原始 SystemUI dump；
只保存模块自有状态与断言计数，报告权限 0600 且不覆盖已存在报告。新签名首次迁移不能使用覆盖安装参数。

`tests/*.ps1` 保留为上游历史回归工装，不删除旧断言，也不把它们算成已由 Gradle 全量覆盖。
历史 `RingSlotsTest` 的三分区空布局字符串预期落后于当前四分区编码，尚不计入通过集合。
状态机、编辑队列、窗口策略、版本比较与兼容性画像已纳入 app 的 Gradle 测试；
Schema 逐字段存储/传输/复制及更新清单校验纳入 render-tests。当前共 27+12 个 JUnit 测试方法，
每个方法内部可包含多项断言，不能与 Android 探针计数相加冒充相同粒度。

附加 `--compatibility-report` 可验证 app 到 SystemUI 的真实报告通道；
它需要 debug instrumentation，不能与 `--skip-probes` 一同使用。

Wrapper 与依赖校验规则见 [TOOLCHAIN.md](TOOLCHAIN.md)。

持续输入探针会打开设置页并暂时保持亮屏，运行前请解锁设备。它在约两秒内发送 240 次编辑，
以 20ms 的慢持久化验证 80ms 合并上限，并与突发合并、字体绑定等检查合计为 Find N5 上的 312 项断言。
所有缓冲测试使用独立偏好文件，结束后清理；不改真实用户配置。
