# 当前测试入口

普通提交执行：

```sh
./gradlew :app:testReleaseUnitTest :render-tests:test :host-tests:test :app:lintRelease :app:assembleRelease
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

`tests/*.ps1` 中八个入口已转发到 Gradle，其余仍保留为历史回归工装；不把全部历史工装算成已经迁移。
历史 `RingSlotsTest` 的过期三分区、聚合信号及中部信号行预期已在新 `FreeRingTest` 中按当前规则迁移；
原脚本仍保留，不能据此宣称原脚本本身已通过。
状态机、编辑队列、窗口策略、版本比较与兼容性画像已纳入 app 的 Gradle 测试；
Schema 逐字段存储/传输/复制及更新清单校验纳入 render-tests。当前 app/render 共 33+39 个 JUnit 测试方法，
每个方法内部可包含多项断言，不能与 Android 探针计数相加冒充相同粒度。

`GlyphScaleTest` 将旧脚本的 17 项真实像素断言迁为 1 个 Gradle/JUnit 方法；
`FreeRingTest` 的 6 个方法覆盖 624 项当前配置/渲染断言，其中 126 个合法组件/区域/尺寸帧进行像素隔离检查。
整体缩放分别用忽略缩放、取消限幅和重复缩放故障对照验证；组件测试另验证反向优先级、丢失旧信号迁移、组件偏移三种故障可检出。
历史脚本迁移状态及下一步边界见 [优化续作清单](validation/optimization-follow-up-2026-10-26.md)。

`HyperOsIconLeaseTest` 的 9 个方法直接执行生产 lease 类，覆盖排队后换配置、只撤回模块增加的屏蔽项、
多个 manager 隔离、灵动岛切换、两个移动 slot 别名、原生电池子视图恢复、延迟字段解析和 OEM 失败。
17 项原交接断言在提取前后均通过。旧 ring-handoff 脚本的源码列表/fixture 已同步；
macOS 没有 pwsh，本轮通过等价 Java 编译/执行核对工装，未声称执行过 PowerShell 引擎。

`host-tests` 将 Drawable 宿主协议与渲染像素替身隔离。5 个参数化实例保留历史 156 项断言，
另一个方法验证字段写入失败的直绘回退。直接编译生产 GlyphDrawable/Refl，生成当前实际 Hook 方法体的测试夹具；
标记/边界改变即失败，不自动换用其他源码位置。普通 CI 和发版 CI 均已加入 `:host-tests:test`。
host-tests 另迁入 18 组 Wi-Fi 组合/3 次转换和 44 项电池读数断言，增加温度充电过滤和原集成约束检查。
八个旧 PS 入口已统一转发，Drawable 与 OS4 strip 的替代源码目录参数经过 Gradle 实跑验证。
`host-tests:readoutTest` 还保留 76 项读数布局断言与 4 组集成约束，使用独立替身/报告；host-tests:test 自动依赖它。
`CapsuleTest` 保留 78 项胶囊轮廓/电量/字体断言；`CapsuleSourceContractTest` 的 4 个方法保留测量、拟合、样式入口约束，
并检查动态长度及上下组件的额外宿主高度。字号/字重独立性按真实文字绘制的独立像素层判断，避免旧固定分界误判。
`CapsuleZonesTest` 的 3 个方法覆盖 338 项四格编辑/像素/信号断言，包括两档长度下的 32 组极值帧。
文字可沿整个 body 轨道增长，像素检查要求留在 body 且存在可编辑区域；左右锚点单独核对其命中区域。
`host-tests:stripTest` 使用独立 ViewGroup 替身保留 2776 项 OS4 布局/预留检查，另补 144 项排序与优先级断言，
同样由普通 host-tests:test 自动调用。
`RingHitBoundaryTest` 两项检查覆盖 18 个极值描边/布局/宿主比例帧的逐像素命中和 ink 外点击拒绝。
目前统计为 app 33、render 39、host 21（普通14+隔离读数5+环外布局2）个 JUnit 方法/实例。详见 [host-tests](../host-tests/README.md)。

附加 `--compatibility-report` 可验证 app 到 SystemUI 的真实报告通道；
它需要 debug instrumentation，不能与 `--skip-probes` 一同使用。

Wrapper 与依赖校验规则见 [TOOLCHAIN.md](TOOLCHAIN.md)。

持续输入探针会打开设置页并暂时保持亮屏，运行前请解锁设备。它在约两秒内发送 240 次编辑，
以 20ms 的慢持久化验证 80ms 合并上限，并与突发合并、字体绑定等检查合计为 Find N5 上的 312 项断言。
所有缓冲测试使用独立偏好文件，结束后清理；不改真实用户配置。
