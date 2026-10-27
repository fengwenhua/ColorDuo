# ColorOS 状态栏高度与圆环尺寸

## 需求与基线

- 维护者明确选择整体状态栏变高，同时放大圆环／胶囊；入口位于首页总开关下。
- Find N5 内屏：2248×2480、520dpi。状态栏原生窗口及 statusBars／tappableElement inset 均为 107px；模块电池绘制区域原生高 52px，调大画布内缩放无法突破这个宿主边界。
- JADX 确认 `PhoneStatusBarView.updateStatusBarHeight` 更新状态栏 View 与 system_icons；`updateWindowHeight` 调用原生 WindowController 更新窗口及各旋转的 InsetsFrameProvider。

## 首个候选实现（1.1.12，后续按反馈调整）

- 全局 `status_bar_height_percent`，默认 100，范围 100～150，UI 步长 5；圆环／胶囊和充电场景共用，不替换现有布局。
- SystemUI 内挂接原生 SystemBarUtils 高度计算，以原生最终结果按倍率增高；嵌套调用只缩放一次，保留原有摄像头／瀑布屏最小高度。
- 设置变化调用原生 View／窗口更新路径，system_icons 行高同步调整，应用顶部预留空间由原生提供的 Insets 更新；不修改系统分辨率、density 或系统文件，不增加 android 作用域。
- 桌面和普通应用的 PhoneStatusBarView 内，电池 ImageView 宿主按相同比例放大，沿用原有尺寸租约和呈现后接管流程；其他独立的锁屏／QS 宿主保留自身布局。
- 100% 与关闭总开关均回到原生高度；未支持的 ROM 不安装高度 hook，UI 提示 ColorOS 限定。
- 配置 Schema 仍为 VERSION=1，字段总数 63；旧配置缺少新项时默认 100%。

## 自动检查与待验收

- 本地 JUnit 171 项（app 55／render 75／host 41）通过；新增倍率、最小值、边界溢出、关闭恢复、历史配置、样式／充电独立性检查。
- Python 22 项、release 构建通过；Lint 0 errors，正式包不含 Observer。
- 待同签名包真机验证：100% 基线、120%／150% 窗口及应用 Insets、圆环／胶囊绘制尺寸、恢复默认／关闭恢复、折叠与旋转。暂不声称该阶段已完成。

## 1.1.12 真机反馈后的调整

- 1.1.12 同签名候选已安装，窗口基线为 107px；维护者反馈恢复默认后图标挤向顶部，且要求时钟、通知及左右所有图标一起放大、与摄像头对齐。
- 日志确认恢复时窗口与 Insets 已回到 107px，但 ColorOS 内容区仍保留增高时的 46px 底部留白。已重载 SystemUI 清理该临时状态。
- 1.1.13 改为整组缩放：保留原生电池尺寸租约，左侧时钟／通知与右侧整组按同一倍率绘制，在各自可用宽度内重新测量，保留物理左右边界和摄像头避让区域。
- ColorOS padding provider 的原生计算使用未放大的系统高度，增高后以其物理摄像头上下边界确定内容区中心；内容带增长受新窗口边界约束。每次变更同时调用原生 updateContentsPadding，避免残留留白。
- 100%／总开关关闭时，恢复原生尺寸、gravity、缩放、pivot 和留白；内外屏／旋转重新测量。独立锁屏／QS 宿主不进行整组缩放。
- 新增内外屏摄像头中心不变、内容带不越界和恢复原始 padding 的回归检查；仍待最终候选真机验证。

## 暂停接续点：维护者已带走手机

维护者要求继续不依赖手机的工作，直到真机步骤再暂停。本阶段已完成代码、自动检查、同签名包准备与校验，停在安装／验收前。

- 当前待验收源码：`1b2ad63`，tag `v1.1.13`，版本 `1.1.13 / 10113`。
- [main CI 40484439843](https://github.com/fengwenhua/ColorDuo/actions/runs/40484439843)、[tag CI 40484439902](https://github.com/fengwenhua/ColorDuo/actions/runs/40484439902)、[同签名候选构建 40484789817](https://github.com/fengwenhua/ColorDuo/actions/runs/40484789817) 均成功。publish 关闭，未公开发版。
- 本地 JUnit **173 项**（app 57／render 75／host 41）、Python 22 项通过；Lint 0 errors、32 warnings、1 hint，release 构建通过，Observer 未打包。
- 候选 APK：`.local/statusbar-candidate-1.1.13/ColorDuo-1.1.13.apk`；artifact `12724444128`。SHA-256：`36beb8f45a573de81255e16d2c83739fef5b2efa06af4f656e4c0a7bc8eaed9c`。
- APK 版本、包名、生产证书、v2/v3 与 manifest 签名核验通过；生产私钥仍只在受保护的 GitHub 环境。
- 手机最后安装的是 **1.1.12 测试包**；恢复 100% 并重载后，最后一次采集为窗口 107px、内容区 padding `25,0`，未残留 46px 底部留白。维护者带走手机后未再执行 ADB 操作，1.1.13 尚未安装。
- 公开稳定版仍为 **1.1.11 / 10111**；不得把 1.1.12／1.1.13 候选写成已验收或已发布。

回来后继续：

1. 重新确认 ADB 是 Find N5；临近覆盖安装读取当前配置，安装已校验的 1.1.13 同签名包，不回灌旧偏好。重载 SystemUI，先核对 100% 原生布局。
2. 外屏 120%／150%：核对整行时钟、通知与右侧内容同比放大；读取 `statusBarHeight` 的左右 scale／centreY、原生 camera hole 参数、状态栏窗口和 Insets，并对照实际屏幕。确认不越过摄像头左右避让区域。
3. 多次执行增高→恢复默认、总开关关闭／开启；验证缩放、gravity、pivot、宽高和 padding 全部恢复，没有挤向顶部或空缺。
4. 切换圆环／胶囊，展开／合上及旋转，检查重测与恢复。锁屏／QS 的独立布局不作为整组缩放对象，但需确认没有被全局高度路径破坏。
5. 真机通过后再记录验收；正式发布需维护者明确授权，并使用与已验收源码 tag 一致的产物。
