# ColorOS 状态栏高度与圆环尺寸

## 需求与基线

- 维护者明确选择整体状态栏变高，同时放大圆环／胶囊；入口位于首页总开关下。
- Find N5 内屏：2248×2480、520dpi。状态栏原生窗口及 statusBars／tappableElement inset 均为 107px；模块电池绘制区域原生高 52px，调大画布内缩放无法突破这个宿主边界。
- JADX 确认 `PhoneStatusBarView.updateStatusBarHeight` 更新状态栏 View 与 system_icons；`updateWindowHeight` 调用原生 WindowController 更新窗口及各旋转的 InsetsFrameProvider。

## 实现

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
