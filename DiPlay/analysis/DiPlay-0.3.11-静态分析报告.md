# DiPlay 0.3.11 静态反编译与本田车机适配分析

> 分析对象：仓库根目录 `diplay0.3.11.apk`  
> APK SHA-256：`22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`  
> 反编译运行：GitHub Actions Run #4（成功）  
> 反编译产物：`diplay-0.3.11-decompiled`，约 55.6 MB  
> 分析日期：2026-11-06

## 1. 结论摘要

DiPlay 0.3.11 是一个功能较完整的 CarPlay 实现，包含 iAP2/MFi、USB、蓝牙、无线热点、网络隧道、媒体与车机 HUD 等模块。它并非只依赖 Android 标准 API：热点模块同时使用 Android Wi-Fi API、隐藏的 `IWifiManager` Binder 接口、ROOT Shell、`hostapd`/`dnsmasq`、`app_process` 以及若干原生库。

本田目标车机基准为 Android 4.2.2 / API 17、ARMv7（`armeabi-v7a`），且设备已取得 ROOT。APK 的 `minSdkVersion=17` 和 ARMv7 原生库 ABI 与目标系统表面匹配，但这**不代表 APK 可直接稳定运行**。应用的 `targetSdkVersion=37`、较新的 AndroidX/Kotlin 依赖、API 26+ 热点路径、现代前台服务声明和厂商专用桥接都必须经过 API 17 实机验证。

当前建议：保留其 CarPlay 协议和传输层作为研究基线；将 BYD 专用集成、现代 Android 热点 API 和 ROOT 热点后端拆成可替换适配层。针对本田车机，优先做只读检测与能力探测，再决定使用 ROOT hostapd、旧版 Wi-Fi AP 反射接口或 Wi-Fi Direct 路径。

## 2. APK 基本信息

| 项目 | 反编译结果 |
|---|---|
| 应用标签 | DiPlay |
| APK 包名 | `com.shihab.diplay` |
| 主要代码命名空间 | `com.shilapi.xcertplay` |
| 版本 | 0.3.11 |
| versionCode | 131 |
| minSdkVersion | 17 |
| targetSdkVersion | 37 |
| compileSdkVersion | 37 |
| debuggable | true |
| usesCleartextTraffic | true |
| 权限声明 | 29 项 uses-permission |
| 原生库 | 9 个，均位于 `armeabi-v7a` |

应用 Manifest 中存在 BYD 专用权限 `android.permission.BYDAUTO_INSTRUMENT_COMMON`，并查询 `com.byd.clusterdebug`、`com.byd.carsettings`、`com.byd.appmgr`、`com.byd.deviceinfo`、`com.byd.service` 等包。这些是厂商耦合点，不能假设本田系统存在对应服务。

Manifest 还声明了启动 Activity、CarPlay Host Activity、USB 附件处理、VPN 服务、前台会话服务、蓝牙探测服务、辅助功能服务、HUD/Cluster 相关 Activity/Receiver 等组件。Manifest 中的 `debuggable=true` 与明文流量允许项应在正式适配包中关闭或收紧。

## 3. 反编译产物与完整性

本轮 GitHub Actions 已完成以下步骤：

1. APK SHA-256 校验通过。
2. 安装 JADX 3.0.3 与 APKTool 2.11.1。
3. JADX 输出 Java-like 源码；JADX 退出码为 3，日志报告 104 个反编译错误。因此 Java 源码中可能有少量类/方法不完整，不能把 JADX 输出当作无损源代码。
4. APKTool 成功解码 Manifest、资源、Smali 和原生库。
5. 生成分析说明并成功上传 Actions Artifact。

统计结果：
- 应用主命名空间下约 730 个 Java 源文件。
- 约 19,181 个 Smali 文件。
- APK 主代码 DEX 对应 4 个 Smali 分包；另有 `assets/hotspot/wifi.dex` 与 `assets/hotspot/bt.dex` 两个运行时辅助 DEX，因此静态检查共涉及 6 个 DEX 代码单元。
- 资源文件约 1,265 个。
- 9 个 ARMv7 原生库：`libconscrypt_jni.so`、`liblegacy_ncm_claim.so`、`liblocal_hotspot_radio.so`、`liblwip_ncm_relay.so`、`libopus_microphone.so`、`libspeex_echo.so`、`libusb_host_compat.so`、`libxcertplay_chacha.so`、`libxcertplay_i2c.so`。

涉及复杂控制流或 JADX 报错的部分，后续以 APKTool Smali 为准，并用原始 DEX 字节码交叉验证。

## 4. 关键模块结构

| 模块 | 代表代码 | 功能判断 |
|---|---|---|
| 会话编排 | `orchestration/CarPlayController` | 管理有线/无线连接、启动状态、重试与诊断 |
| iAP2 / MFi | `mfi/`、`iap2/`、`transport/` | Apple 配件认证、配对记录、会话和协议传输 |
| USB | `transport/IphoneUsbHost`、`DirectUsbHost`、`NcmUsbBridge` | USB Host、iAP2、NCM 网络桥接 |
| 蓝牙 | `BluetoothRfcommDuplexStream`、`usbbt/` | RFCOMM、USB 蓝牙适配器、HCI/L2CAP/RFCOMM |
| 无线热点 | `network/CarHotspotTethering`、`HostapdHotspotManager`、`ManualHotspotManager` | 系统热点、ROOT hostapd、手动热点和 Wi-Fi Direct 等路径 |
| ROOT 执行 | `network/RootShell` | 通过 `su` 执行命令、超时控制、读取输出和写文件 |
| HUD / Cluster | `hud/`、`AdbClusterActivity` | 车机仪表/HUD/厂商集成，含明显 BYD 相关接口 |
| 媒体与音频 | `media/`、`airplay/` | 音视频、麦克风、音频路由及 AirPlay 相关能力 |

## 5. ROOT 热点模块的实际行为

### 5.1 ROOT Shell

`RootShell` 使用 `ProcessBuilder("su")` 启动 root shell，通过 `id` 检查输出是否包含 `uid=0`。脚本执行前设置 `TMPDIR=/data/local/tmp`，并实现超时等待和输出读取。它还提供写文件方法。

本田设备报告已确认 ROOT 可用，但仍需在目标系统上验证：
- `su` 是否允许 DiPlay 进程获得 root；
- SELinux/传统权限模型是否允许访问 Wi-Fi 配置目录、网络接口和 Binder 服务；
- shell 中的 `ifconfig`、`ip`、`ps`、`app_process`、`hostapd`、`dnsmasq` 是否存在且参数兼容。

### 5.2 hostapd 后端的硬编码前提

当前代码将以下值写死：
- 无线接口：`wlan0`
- 驱动：`nl80211`
- 配置文件：`/data/misc/wifi/hostapd.conf`
- 临时目录：`/data/local/tmp/`
- 热点地址：`192.168.42.1/24`
- DHCP 地址池：`192.168.42.10–192.168.42.50`
- 频段/信道：2.4 GHz，信道 1–13
- 服务：要求系统可执行 `hostapd` 与 `dnsmasq`
- hostapd 配置使用 WPA2-PSK/CCMP，并可附加 Apple Vendor IE。

启动链会先关闭框架 Wi-Fi（通过部署的 `wifi.dex`/WifiCtl），清理旧 hostapd/dnsmasq 进程，再将生成配置复制到系统 Wi-Fi 目录，启动 hostapd、给 `wlan0` 配置静态地址，最后启动 dnsmasq。该实现对 Android 4.2.2 这类老系统的接口布局有一定针对性，但是否能在本田固件运行取决于驱动是否支持 AP 模式和 nl80211。

### 5.3 必须先修复的系统配置风险

静态代码显示：启动时会直接覆盖 `/data/misc/wifi/hostapd.conf`；当前 teardown 主要清理 hostapd/dnsmasq、关闭 `wlan0` 并重新启用框架 Wi-Fi，没有看到对原始 `hostapd.conf` 的可靠备份恢复流程。

这可能造成系统原热点配置丢失或后续 Wi-Fi 行为异常。后续适配必须增加：
1. 修改前备份原文件（保留权限、属主、SELinux 标签和时间戳）。
2. 记录本次操作前 Wi-Fi 开关状态、接口状态、IP/路由、相关进程 PID。
3. 只在所有前置探测成功后执行变更。
4. 使用事务标记记录每一步是否成功；任何步骤失败都按逆序回滚。
5. 退出/崩溃/重启后可恢复；恢复时校验备份文件完整性。
6. 禁止无差别杀死系统已有 hostapd/dnsmasq；必须先确认进程归属和启动参数。
7. 对 SSID、密码、路径和 shell 参数做严格校验及安全转义，禁止拼接未经验证的用户输入。

这部分是本田适配优先级最高的 ROOT 模块改造项。

## 6. Android 4.2.2 / API 17 适配风险

| 风险点 | 原因 | 适配方向 |
|---|---|---|
| 新版热点 API | `LocalOnlyHotspot` 属于 API 26+，API 17 不存在 | 运行时能力探测；API 17 走旧 API/ROOT 后端 |
| 隐藏 Wi-Fi Binder | 代码反射 `android.net.wifi.IWifiManager` 的方法名和签名 | 在本田固件上检查 ServiceManager、IWifiManager、方法签名和权限 |
| 前台服务/通知权限 | Manifest 含较新的前台服务类型、通知和附近 Wi-Fi 权限 | API 17 分支不得调用新权限/新服务 API；用兼容通知和 Service 实现 |
| AndroidX/系统类 | 编译 SDK 37，运行环境 API 17，部分新类/方法可能触发 VerifyError 或 NoSuchMethodError | 用 API 17 emulator/实机启动测试，逐个修复类加载和方法调用问题 |
| 厂商 API | BYD Cluster、车设置、设备信息、专用权限和服务 | 从 Honda 构建中隔离，改为通用接口/可选插件 |
| Wi-Fi 驱动 | `wlan0`、`nl80211`、hostapd 和 AP 模式均为硬件/内核相关 | 先只读探测接口、驱动、频段、hostapd/dnsmasq，再选择后端 |
| ARM ABI | APK 仅携带 `armeabi-v7a` 原生库 | 与报告中的 ARMv7 设备方向匹配，但仍需检查每个库的动态依赖和 NDK/API 要求 |

注意：`minSdkVersion=17` 只表示打包时允许安装到 API 17，不代表所有业务路径在 API 17 都可执行。必须做真实设备验证。

## 7. 针对本田车机的实施路线

### 阶段 A：只读兼容性探测（先做，不改系统）
- 读取 `Build.VERSION`、ABI、包安装状态、ROOT 状态。
- 读取 Wi-Fi 服务、接口名称、接口 MAC、当前 IP/路由、驱动信息。
- 探测 `hostapd`/`dnsmasq` 路径及版本，仅执行 `--help`/版本查询，不启动进程。
- 检查 `/data/misc/wifi` 目录权限、现有配置文件是否存在及可否读取。
- 通过只读方式探测 `IWifiManager` Binder 服务和旧版热点方法。
- 将探测结果导出为中文诊断报告。

### 阶段 B：适配层重构
建立统一接口 `HotspotBackend`，将后端拆分为：
- `LegacyWifiApBackend`：仅在旧系统方法确实存在且可用时启用。
- `RootHostapdBackend`：只有驱动、二进制、权限、配置备份全部通过时启用。
- `WifiDirectBackend`：只有固件 P2P 实现可用时启用。
- `UnsupportedBackend`：明确显示原因，不进行危险的半启动操作。

### 阶段 C：ROOT 事务保护
- 建立 `HotspotSnapshot`、`HotspotJournal`、`HotspotRollback`。
- 对配置文件做备份/校验/恢复。
- 将启动与清理拆成幂等步骤。
- 加入取消、超时、重启恢复和诊断日志。
- 默认只读；由用户明确确认后才写系统分区/配置路径。

### 阶段 D：车机实测
先验证 Wi-Fi 基础状态，再验证热点广播、手机关联、DHCP 分配、CarPlay TCP 连接和退出恢复。每次只更改一个变量，保存前后日志和配置快照。

## 8. 下一步执行清单

- [x] 将原始 APK 纳入 GitHub 仓库并校验 SHA-256。
- [x] GitHub Actions 自动安装 JADX/APKTool。
- [x] 完成 Java-like 源码和 Smali 反编译。
- [x] 上传完整反编译 Artifact。
- [x] 完成首轮模块与兼容性静态分析。
- [ ] 将本报告提交到仓库并继续补充类级调用关系。
- [ ] 生成 API 17 不兼容 API 清单（按调用点、所在类、保护条件分类）。
- [ ] 检查 9 个原生库的 ELF 依赖、导出符号和最低系统要求。
- [ ] 完成本田专用热点只读探测器。
- [ ] 实施 ROOT 配置备份/回滚改造。
- [ ] 构建中文适配 APK 并在目标车机验证。

## 9. 产物与仓库链接

- 原始 APK：`/diplay0.3.11.apk`
- 反编译工作流：`.github/workflows/diplay-decompile.yml`
- 反编译产物：GitHub Actions Artifact `diplay-0.3.11-decompiled`
- 本报告：`DiPlay/analysis/DiPlay-0.3.11-静态分析报告.md`
- GitHub Actions Run #4： https://github.com/qiangge5586-a11y/1/actions/runs/42140451737

> 本报告基于 APK 静态反编译，不代表已在本田车机完成安装、热点启动或 CarPlay 实测。所有涉及 ROOT 的后续修改都必须先完成只读探测和备份恢复机制。
