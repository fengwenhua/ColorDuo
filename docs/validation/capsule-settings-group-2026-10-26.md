# O-08 胶囊几何配置组

在 O-02 发布恢复补强提交 726202a 的 CI 40336746920 通过后实施本步骤。

## 实施范围

五个平铺 Java 字段迁入不可变 CapsuleLayoutSettings：左右两列的 x/y 位移及长度。
TrioSettings 保持聚合快照，copy 共享不可变组，equals/hash 使用整组值。
SharedPreferences 与 Bundle 通过同一个 IntReader 入口读取，默认值/左右范围/长度限幅逐项保留。
单 key 更新使用对应 with 方法，保存其他四项，避免远端部分更新反向覆盖本地新值。

内部 Java/Kotlin 访问改为 capsuleLayout 的访问器；TrioAppearance 对渲染器暴露的已解析字段不变。
持久化和广播仍为原五个扁平 key，Schema VERSION=1、逻辑字段数 61、样式/充电维度不变。
没有改 Renderer/Geometry 的绘制数学，没有更换签名、安装 APK 或改变手机配置。

## 对照与回归

提取前保存当时已编译的真实模型/Renderer/Geometry/shims，使用同一个 Java 探针分别运行旧、新类路径。
两种样式 × 五个字段 × 七档输入（含超界与零值）共 70 组；每组同时核对规范 Bundle、偏好读取、copy/相等 hash 契约，
并渲染普通/充电两帧。全部规范线值及 140 个像素 SHA-256 相同。
这些是同机同工具链的前后对照，不是跨平台固定 golden，也不计为 Android GPU 实测。

新增三个 JUnit 方法：副本编辑不改原快照及线值、每个单 key 更新都保留其他四个非默认值、
缺失几何值的偏好/Bundle 读取使用相同默认。
原 Schema 全字段测试展开不可变组，旧 issue 反射 sweep 也遍历五个 component，原 663 项运行时断言未减少。

临时源码副本将单 key 更新改为整组覆盖、丢弃右侧 y 更新、改变缺失长度默认值，三种故障均被新测试拒绝。
替代源码目录仍支持原最小 8/9 文件；可另放 CapsuleLayoutSettings.java 覆盖新组。
目录中注入默认值故障会失败，证明新的可选源码确实进入被测类路径。

app/render/host 为 35/63/41，共 139 个 JUnit 方法/实例；Lint、debug/release 构建通过，正常 release 无 Observer。
原始对照、故障日志及探针只存于忽略目录 .local/capsule-model。
本步骤是 O-08 第一组，Content、环布局、Appearance 和最终单声明扩展验收仍继续进行。
