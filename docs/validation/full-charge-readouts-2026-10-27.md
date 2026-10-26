# ColorOS 满电后的温度与功率

本项在 1.1.6 已发布至主仓与模块仓、两仓八个公开附件逐字节核对后实施。
1.1.6 正式运行 40358463996、文档 CI 40359821923 均成功。

## 现场

Find N5 保持 USB 插电，电量 100%，Android status=5，模块原生状态 charging=false / full=true / plugged=true。
读数模块却保持 attached=true，SystemUI dump 为 watts=0.770369、temperature=33，并继续 DRAWN。
用户早前看到的是 0.9W / 34°C；这说明采样仍在更新，主要缺陷是把“插电”当成“正在充电”的显示条件。
当前同时开启功率、温度以及“温度仅充电时显示”，维护者要求两项一起出现和消失。

## 规则与实现

ColorOsReadout.charging 只接受实际 CHARGING 状态且有外接供电；FULL、NOT_CHARGING、DISCHARGING 和 UNKNOWN 均不成立。
不通过电量百分比推断充电状态，因此到达 100% 但系统仍明确 CHARGING 的阶段仍遵循系统状态。

State 的显隐依据由 attached 改为 charging，并把充电状态纳入去重比较。
即使数值和温度相同，充电/完成切换仍会通知绘制；满电不再把可能缓存的残余电流解释为充电功率。
停止充电时功率值为未知；温度仍保持采样，便于关闭“仅充电显示”后显示当前温度。

同时开启两项并勾选仅充电模式时，必须处于充电状态且两项数据均有效才输出两行，否则整组为空。
单独开启一项时保持该项独立；取消温度仅充电模式时，满电仍可单独显示温度，功率保持隐藏。
HyperOS 路径、设置存储、组件排序和宿主接管逻辑未修改。

新增五组 JUnit 保护充电→满电→恢复、暂停/断电/未知、AC/USB/无线、去重与温度刷新、成组缺失值与独立开关。
测试版本为 1.1.7/10107；当前原始记录、配置快照和后续候选验收资料保存于忽略目录 .local/full-charge-readouts。

## 本地检查

app/render/host 为 45/69/41，共 155 个 JUnit 方法/实例；Lint、release/debug 构建及 22 项 Python 测试通过。
十个读数测试另行在桌面 JVM 运行，随后分别把充电判断退回“只看插电”、移除成组完整性限制，
两种故障均被对应的新测试拒绝。Android 调试探针仅修正旧描述文字，本轮未将其计为真机执行。

## 1.1.7 同签名真机验收

修复提交 9b89378，普通 CI 40363475806 通过，签名候选运行 40363475170 通过。
APK SHA-256：`d7441541b5ac1c4802ca0312ba1a1debf873580efa534cc16c1e69c2c45e8cd2`。
核验生产证书、v2/v3、清单签名、版本与源码后覆盖安装，设置保留。

手机仍为 USB powered=true、status=5、level=100；原生状态 charging=false / plugged=true / full=true。
模块变为 readouts active=true / charging=false / watts=NaN / temperature=31。
圆环宿主 85×52，outside=0，pending=false / failed=false，保持 DRAWN；没有为已隐藏读数留下空位。
仅安装时明确重载一次系统界面，后续维护者切换并确认圆环、胶囊均正常，已授权正式发布。
本轮物理验收针对满电状态；恢复实际充电、暂停和缺失值路径由上述单元回归覆盖。

正式发布运行：[40365549737](https://github.com/fengwenhua/ColorDuo/actions/runs/40365549737)。
正式 APK 与手机上验收的候选 APK 逐字节一致；发布使用正式运行的 APK、摘要、清单和清单签名。
源码仓与模块仓首页同步至 1.1.7，保留新签名版本覆盖升级、低于 1.1.0（不包含 1.1.0）需卸载重装的提醒。
