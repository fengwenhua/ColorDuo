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

## 真机待验收

- Find N5：VPN 已连接时加载模块即高亮，不下拉状态栏；开关 VPN 能立即切色与恢复。
- Wi-Fi／移动网络，圆环／胶囊，自选颜色持久化，总开关及折叠恢复。
- 保留原有用户配置，使用受保护 Actions 签名的同签名测试包覆盖安装。
- HyperOS 兼容路径保留，暂无 HyperOS 真机验收证据。
