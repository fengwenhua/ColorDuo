# VPN 网络图标高亮

## 实现边界

- `VpnStateSource` 监听 VPN transport，状态不依赖快捷设置展开；不要求 INTERNET／VALIDATED，支持本地 VPN。
- Android 11 及以上清除请求默认 capabilities；Android 10 移除默认的 NOT_VPN、TRUSTED、NOT_RESTRICTED。
- 多条 VPN 网络以集合归并，重复通知及新旧网络交接不误判断开；停止监听时注销 callback，旧 session 的迟到事件忽略。
- ColorOS 跟随三合一有效 host 的监听生命周期，VPN 变化使绘制帧失效；HyperOS 使用现有进程级状态与 host 刷新路径。
- `vpn_network_color` 默认 `0xff2196f3`，由「组件 → VPN」修改；圆环、胶囊共用。Wi-Fi、移动信号、网络类型使用高亮色，其余组件保持原色。
- 配置字段总数由 61 增至 62；保留既有 key 与 Schema VERSION=1，缺少新字段的历史配置回退默认蓝色。

## 自动检查

- VPN 网络集合：首次连接、重复事件、断开、重叠交接、清空后重连。
- 绘制：圆环／胶囊，Wi-Fi／移动网络，默认／自选颜色，断开恢复，几何与非网络文字颜色不变。
- 配置：默认值、旧配置回退、Bundle、copy、equals、hash、局部更新与样式共用。
- 本地完整 JUnit：app 48、render 72、host 41，共 161 项，0 失败；Python 22 项通过。
- ColorOS 策略脚本共 82 项通过；Wrapper 与正式包不含 Observer 的检查通过。
- Lint：0 errors、26 warnings、1 hint；debug/release 构建通过。
- 真机验收在完成后补记；以上自动检查不代表已完成真机验证。

## 同签名候选包

- 功能源码：`67a060927997f4d4c4855a4c41870a93ea01af3b`，tag `v1.1.8`，版本 `1.1.8 / 10108`。
- main 与 tag 的构建 CI 均成功：[40451254802](https://github.com/fengwenhua/ColorDuo/actions/runs/40451254802)、[40451255147](https://github.com/fengwenhua/ColorDuo/actions/runs/40451255147)。
- [签名候选构建 40451329959](https://github.com/fengwenhua/ColorDuo/actions/runs/40451329959) 的 build/sign 成功，publish 跳过；未创建公开 Release。
- APK、SHA 文件、manifest 与 manifest 签名经核验，包名／版本／生产证书及 v2/v3 签名均正确；无 debuggable 标记。
- APK SHA-256：`90c380eff9387415d004cd43b9f3c1d9b5fe25e598e2920d16dc9e6bd66d2aca`。
- 已覆盖安装到 Find N5，安装前后配置 XML 完全相同；从手机回拉的 APK 与候选包逐字节哈希一致。
- SystemUI 重载成功，模块 dump：`vpn listening=true active=false error=None`，与原生 VPN 未开启状态一致；未发现相关异常。
- 真机 VPN 开启／断开、自选颜色及样式切换仍待完成，不能据此声称功能已全面验收。

## 真机待验收

- Find N5：VPN 已连接时加载模块即高亮，不下拉状态栏；开关 VPN 能立即切色与恢复。
- Wi-Fi／移动网络，圆环／胶囊，自选颜色持久化，总开关及折叠恢复。
- 保留原有用户配置，使用受保护 Actions 签名的同签名测试包覆盖安装。
- HyperOS 兼容路径保留，暂无 HyperOS 真机验收证据。

## 维护者反馈后的修正（1.1.9）

- 维护者已确认 1.1.8 的圆环、胶囊均能在 VPN 开启后变蓝，但要求四个信号点保持原色，并反馈下拉右上角仍有原生 VPN 标识。
- 高亮范围收窄为 Wi-Fi 与网络类型；环内点阵、环外信号条保留原色。上文 1.1.8 对移动信号整体高亮的描述是历史行为。
- JADX 确认 ColorOS `OplusPhoneStatusBarPolicyExImpl.access$updateVPN` 单独更新 `vpn` 槽位；原生标签不会因模块自行高亮而消失。
- 新增局部 ignored-slot 接管：仅在同一状态栏实际画出 VPN 高亮后隐藏该行原生 VPN 槽位；没有 Wi-Fi／网络类型、透明高亮色、停止接管或停用模块时恢复。系统 VPN 状态、控制器与通知不修改。
- 多个 host 共用一个容器时最后一个交还才恢复；模块启用前已经隐藏的槽位不被误恢复；重复帧不重复触发布局。
- 本地 JUnit 166 项（app 52／render 73／host 41）、Python 22 项通过；Lint 0 errors，release 构建通过。
- 1.1.9 真机接管、下拉与恢复验收待安装后补记。

## 下拉独立标签来源（1.1.10）

- 安装前的截图与 hierarchy 确认：上方标签属于 `privacy_chip`，与 `statusIcons` 的 VPN 槽位独立。1.1.9 仅完成普通槽位接管，候选签名工作流已取消，未安装该包。
- JADX 确认 `OplusQSPrivacyContainerViewBinder.fadeInNewIcons` 先绘制隐私图标列表，再按 QSSecurity 添加 VPN／其他安全模式图像。
- 新 hook 仅在同一 QS 区域已经绘制高亮且 `isVpnMode()` 为 true 时关闭末尾 VPN 图像；隐私图标列表原样传给系统，非 VPN 安全模式保留。
- 没有其他隐私图标时一并收起空背景；交还时读取当前安全可见状态再重绘，原生 fade-out 开始时丢弃旧列表，避免重放已结束的相机／麦克风提示。
- 不修改 SecurityController、VPN 网络状态、showSecurity 配置或系统通知。
