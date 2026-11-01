# 系统外置百分比导致 ColorDuo 内部电量留空

## 现场与原因

Find N5、ColorOS 16、已安装 1.1.20。系统状态栏页面显示「电量百分比：在电池外显示」，圆环内部没有电量数字，右侧保留 98%。截图及只读 SystemUI 现场保存在忽略目录 `.local/issue7/`。对应 [issue #7](https://github.com/fengwenhua/ColorDuo/issues/7)。

ColorOsRingPreset 在探测到原生外置百分比 View 可见时，用 `showValue && !outsidePercent` 关闭内部电量组件；既未接管外侧百分比，也使模块布局随原生 View 可见性改变。

## 修复

- 移除原生百分比可见性对模块电量开关和布局缓存的影响。内部组件按用户的 ColorDuo 配置显示。
- 每个电池宿主独立保存外置百分比的原生可见性。只有替代帧成功绘制、通过既有交接检查且新旧布局均拥有电量组件时，才隐藏重复百分比。
- Hook 精确的 OEM `bind$updatePercentOutView(TextView, StatBatteryMeterView, PercentOutIcon)`；非空原生模型应用后更新恢复状态，并保持已接管的重复数字隐藏。未拦截全局 View.setVisibility，也未写系统百分比设置。
- 关闭、解绑、被隐藏、切换到不接管的布局或绘制失败时走既有 release 恢复；不会把模块自己的 GONE 覆盖为原生状态。增加脱敏 percentIcon 诊断。

## 自动验证

执行真实 ColorOsRingPreset 的 JUnit 回归覆盖圆环、胶囊及验证布局的电量开关；渲染测试直接编译生产 ColorOsPercentIcon，检查提交前保留、提交后隐藏、重复提交、三种可见性、运行中切换、释放、独立宿主及外来可见性变更。桌面 View 替身只验证可见性协议，不代表 OEM Hook 已在设备执行。

本地 JUnit 200 项（Release app 58／render 85／host 57）、Lint 与 Release 构建通过；Python 22 项、Wrapper、签名集成与 Observer 不打包检查通过。同签名候选包与手机验收结果在完成后补充。本轮仅生成测试包，不公开发布。
