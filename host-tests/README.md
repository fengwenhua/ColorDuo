# 宿主与读数回归测试

```sh
./gradlew :host-tests:test
```

独立 Java/JUnit 项目，编译真实 `GlyphDrawable.java` 与 `Refl.java`。
Android Drawable/View 与 Renderer 为本项目专用替身，不与 render-tests 的 Java2D 像素替身混用，也不进入 APK。

GlyphHostTest 的五种实际反射形态来自旧 `tests/glyph-drawable.ps1`：
完整槽位、旧版绘制、无恢复方法、仅非公开恢复方法、无绘制槽。
各自保留原测试的 34/34/31/31/26 项断言；另测试字段类型不兼容时直绘回退且保留外来值。
覆盖首次与连续绘制、重绑定、零尺寸、立即停用、灵动岛、原生 tint 重放和只释放自身占用。

绘制 Hook 的实际 interception 方法体在编译前从当前 TrioHooks 读取，插入隔离模板；
不另写一个路由实现。标记必须唯一，边界缺失即失败；Hook 结构变更后必须复核这个适配器。
它不是通用 Java 解析器，也不验证 Xposed 调度器、MIUI 固件实现或 Android GPU 像素。
View/Renderer 替身只验证传入参数、调用顺序及恢复协议；实际绘制由 render-tests 和设备探针互补覆盖。

Wi-Fi 与电池读数夹具同样从当前生产源码读取 resolve/publish/功率及文本方法，
保留 18 组 Wi-Fi 组合与 3 次重连转换，以及 44 项原电池读数/去重断言，另补仅充电时显示温度。
三组 Drawable 源码约束和两组 Presence 源码约束保留原脚本不能在 JVM 标量模型中观察的集成约束。
这些模型不调用 ConnectivityManager、BatteryManager 或真实 Android 广播投递。

四个旧入口 glyph-scale/glyph-drawable/wifi-presence/battery-presence.ps1 已改为统一 Gradle 启动器，
不再维护另一份 Java 断言或编译清单。Drawable 的 `-SourceDirectory` 仍可指定三个替代生产源文件所在目录：

```sh
./gradlew :host-tests:test --tests io.github.fengwenhua.colorduo.GlyphHostTest -PhostTestProductionSources=/path/to/sources
```

该参数只替换 GlyphDrawable、Refl 与绘制 Hook 来源，不替换 Presence 的正常生产源码。
没有 pwsh 的环境直接运行 Gradle；本轮未把 PowerShell 引擎记为实跑验证。

性能读数的 Paint/View 合同使用独立 readoutTest 源集及 JVM：

```sh
./gradlew :host-tests:readoutTest
```

普通 `:host-tests:test` 依赖此任务，CI 不需要额外命令。原 perf-layout 的 76 项尺寸/行距/预留宽度断言和
4 组集成约束已迁入；42 个当前方法/常量绑定从生产源码读取，要求唯一匹配，不使用历史拷贝。
FontMetrics 的 ascent 按 Android 负值约定，Typeface 使用共享桌面实现；这套替身与 Drawable fixture 隔离。
两个测试报告目录分别是 build/test-results/test 与 build/test-results/readoutTest。
