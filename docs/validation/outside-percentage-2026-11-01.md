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

本地 JUnit 200 项（Release app 58／render 85／host 57）、Lint 与 Release 构建通过；Python 22 项、Wrapper、签名集成与 Observer 不打包检查通过。本轮仅生成测试包，不公开发布。


## 同签名候选与真机验收

- 源码 tag `v1.1.21` → `c2c8c5efde15ee4f199a2cea38567e8477543fbb`，版本 `1.1.21 / 10121`。
- [候选构建与签名 41266125671](https://github.com/fengwenhua/ColorDuo/actions/runs/41266125671)：build、sign 成功；publish 明确跳过。受保护签名任务只生成测试 artifact `13067446369`，未创建正式 Release、同步模块仓或清理历史版本。
- 固定正式证书、APK v2/v3、独立清单签名、版本、包名与源码身份校验通过。APK SHA-256：`cce0e24983f4feefe31bc4435ed5f3f0935593c4c88cb1af841d5bf800fab86c`。
- 通过 `adb install -r --no-incremental` 覆盖安装，回拉已安装 APK 与签名产物逐字节一致；安装前后全部偏好值相同，没有卸载或清空配置。精确重载 SystemUI，PID `835 → 21306`，网络 Hook 与电池 Hook 正常，电池布局 pending=false／failed=false。
- 保持系统 `display_power_percent=2`（在电池外显示），真机圆环内部显示 100，重复的外侧数字隐藏；胶囊内部时间与 100 正常。原生百分比诊断为 owned=true／nativeVisibility=0。
- 圆环、胶囊各自动检查「外置 → 内置 → 不显示 → 外置」，共八个状态；原生百分比恢复状态分别为 0／8／8／0。两种样式均关闭再开启模块，关闭时恢复原生电池及外侧 100%，开启后恢复内部数字且无重复。
- 自动检查结束恢复检查开始时的胶囊、模块开启及系统外置百分比，用户设置语义一致；正常编辑使传输 revision 从 2442 增至 2448，epoch 与快照 payload 不变，没有倒退或回写传输版本。原始截图、脱敏回归摘要与安装校验保存在忽略目录 `.local/issue7/`。
- 维护者随后手动测试圆环、胶囊，并确认在「在电池外显示」下均正常。其他机型（包括 issue 原报告的一加 12）未在本轮真机实测，不以 Find N5 结果代替这些设备的验收。

测试包备份：`dist/candidate-1.1.21/ColorDuo-1.1.21.apk`，同目录保留 checksum 和签名清单。
