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
- [x] 将本报告提交到仓库并继续补充类级调用关系。
- [ ] 生成 API 17 不兼容 API 清单（按调用点、所在类、保护条件分类）。
- [x] 检查 9 个原生库的 ELF 依赖、导出符号和最低系统要求。
- [x] 创建本田车机只读热点能力探测脚本（`DiPlay/tools/honda-readonly-probe.sh`）。
- [ ] 实施 ROOT 配置备份/回滚改造（先根据只读探测结果确定真实配置路径与回滚策略）。
- [ ] 构建中文适配 APK 并在目标车机验证。

## 9. 产物与仓库链接

- 原始 APK：`/diplay0.3.11.apk`
- 反编译工作流：`.github/workflows/diplay-decompile.yml`
- 反编译产物：GitHub Actions Artifact `diplay-0.3.11-decompiled`
- 本报告：`DiPlay/analysis/DiPlay-0.3.11-静态分析报告.md`
- GitHub Actions Run #4： https://github.com/qiangge5586-a11y/1/actions/runs/42140451737

> 本报告基于 APK 静态反编译，不代表已在本田车机完成安装、热点启动或 CarPlay 实测。所有涉及 ROOT 的后续修改都必须先完成只读探测和备份恢复机制。


## 10. API 17 调用点复核（第二轮）

对应用主命名空间进行源码级搜索后，发现项目并非完全没有旧系统兼容逻辑：

- `LocalOnlyHotspotManager` 使用 API 26+ 的 LocalOnlyHotspot 能力，并在较新系统上读取 `SoftApConfiguration`；API 17 不具备这些类/方法，必须确认控制流不会提前解析或调用它们。
- `ManualHotspotManager` 对 API 21 以下存在单独分支；读取现代 SoftApConfiguration 的逻辑被版本判断包围。
- `CarHotspotTethering` 在 API 23 以上才使用 `getSystemService(Class)`，旧系统使用字符串服务名；热点开启还保留旧版 `setWifiApEnabled(WifiConfiguration, boolean)` 反射回退。
- `RootShell` 对 API 26+ 使用带超时的 `Process.waitFor(timeout, unit)`，较旧系统使用轮询 `exitValue()` 的替代实现。
- `CarPlayController` 对 API 21+ 使用 `getNoBackupFilesDir()`，较旧系统回退到 `getFilesDir()`。
- 源码使用的 `StandardCharsets` 映射为 desugar 的 `p006j$.nio.charset.StandardCharsets`，不能简单按 Android 原生 API 版本判断为缺失；仍需验证 desugar runtime 是否完整。
- Manifest 声明了 `BLUETOOTH_CONNECT`、`BLUETOOTH_SCAN`、`NEARBY_WIFI_DEVICES`、通知和前台服务类型等新权限/属性。API 17 不认识的 Manifest 属性通常会被旧系统忽略，但代码中的运行时权限请求和新 API 调用仍必须有版本分支。

因此当前的 API 17 风险重点不是单纯的 minSdk 数值，而是**类验证/类加载、未加版本保护的新 API 调用、AndroidX 依赖最低版本、旧固件 Binder/隐藏 API 差异**。下一轮应以目标车机启动日志和类加载错误为依据，按调用点逐项修复，而不是整体降低 targetSdk 后盲目重打包。


## 11. 原生库 ELF / NDK 最低 API 复核（第三轮）

已从反编译产物提取 9 个 `.so`，使用 ELF Header、`.note.android.ident` 和动态依赖表检查：

| 原生库 | ELF 目标平台标记 | 主要动态依赖 |
|---|---:|---|
| `libconscrypt_jni.so` | Android API 16 / NDK r21d | libc、libm、libdl、liblog |
| `liblegacy_ncm_claim.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `liblocal_hotspot_radio.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `liblwip_ncm_relay.so` | Android API 19 / NDK r25c | libc、libm、libdl、liblog |
| `libopus_microphone.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `libspeex_echo.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `libusb_host_compat.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `libxcertplay_chacha.so` | Android API 19 / NDK r25c | libc、libm、libdl |
| `libxcertplay_i2c.so` | Android API 19 / NDK r25c | libc、libm、libdl |

**对 Android 4.2.2 / API 17 的影响：** 除 Conscrypt JNI 外，其余 8 个库的 ELF Android 平台标记为 API 19。它们在 API 17 上存在动态链接器找不到系统符号、`UnsatisfiedLinkError` 或功能初始化失败的高风险。这个标记不单独证明每个库必然无法加载，但已经足以把原生库兼容性列为 P0 实测项。

适配动作：
1. 逐个确认 Java/Kotlin 中的 `System.loadLibrary` 调用点和对应功能是否在目标车机必需。
2. 在 API 17 环境实际加载并调用最小 JNI 方法，保存 linker 日志；不能只看 APK 安装成功。
3. 对确实需要的库，取得对应 C/C++ 源码后用 NDK 重新以 `android-17` 为目标构建；重新检查未定义符号和 ELF 平台标记。
4. 对非核心库（如 I2C/HUD、音频增强或备用 NCM 路径），先做可选加载与降级，不要因单个可选库加载失败导致主界面崩溃。
5. 如果拿不到原生库源代码，需评估用 Java/Kotlin 或系统已有接口替代；不能直接修改 ELF 标记伪装成 API 17。

本田目标设备为 ARMv7，与这些库的 CPU ABI 匹配；但 **ABI 匹配不等于 Android API/动态链接兼容**。


## 12. 以资料库《思域报告(1).pdf》校准的本田实机基准

已重新检索并读取资料库中的《思域报告(1).pdf》（3 页），后续适配以该报告记录为准，不要求用户重复提供这些信息。

| 项目 | 报告记录 |
|---|---|
| ROOT | 已获取，`su -c id` 验证 UID 0 |
| Android | 4.2.2 / API 17 |
| 系统构建 | `1.F1A4.65` |
| Build 指纹 | `Honda/Andromeda/vcm30t30:4.2.2/1.F1A4.65/138:user/release-keys` |
| 设备型号 | `MY16ADA` |
| 制造商 | Honda / MITSUBISHI ELECTRIC |
| 设备代号 / 主板 | `vcm30t30` / Andromeda |
| CPU | ARMv7 Processor rev 9 (v7l)，4 核，最高 900 MHz |
| ABI | `armeabi-v7a, armeabi` |
| 显示 | 800 × 480，160 dpi，横屏 |
| RAM | 总量 0.94 GB，可用 0.27 GB（检测时） |
| 内部存储 | 总量 2.34 GB，可用 2.18 GB（检测时） |
| Wi-Fi | 报告检测为可用 |

### 对实施计划的直接修正
1. ROOT 权限已经有实机报告证据，不能再把“是否 Root”当成未知前置条件；后续只需在 DiPlay 自身进程中验证授权链是否可用。
2. Wi-Fi 确认可用，但报告没有给出无线接口名、驱动、AP 模式、hostapd/dnsmasq 路径或 Binder 方法签名。因此只读探测脚本的目的，是补齐这些底层细节，不是重新检测 Wi-Fi 是否存在。
3. 可用 RAM 仅约 0.27 GB，需控制后台服务、反编译/诊断日志缓存和 CarPlay 音视频缓冲，避免在车机端运行大型诊断组件。
4. 800×480 横屏是界面适配基准；正式车机 APK 应采用低内存、横屏优先布局。
5. 设备型号为 MY16ADA，硬件平台为 Mitsubishi Electric Andromeda/vcm30t30；不能把“2019 款劲动版”车型信息直接等同于车机硬件代号，软件适配应以实机报告的系统和硬件标识为准。


## 13. 原生库审计自动化复核与 JNI 加载点

已新增 `DiPlay/tools/analyze-native-libs.sh` 和 GitHub Actions 工作流 `DiPlay Native Library Compatibility Audit`。工作流对原始 APK 进行 SHA-256 校验后，直接从 APK 提取 ELF 并读取 `.note.android.ident` 的前 4 字节小端 API 值。最终成功产物（Run #42141939470）确认：

| 原生库 | NDK Android API note | JNI 加载位置（JADX 源码） | 适配关注点 |
|---|---:|---|---|
| `libconscrypt_jni.so` | 16 | `transport/LockdownTlsEngineFactory` | TLS 引擎有显式捕获加载异常及 Conscrypt Provider 回退逻辑，仍需实机验证 |
| `liblegacy_ncm_claim.so` | 19 | `transport/LegacyNcmNative`、`transport/UsbfsIoNative` | 静态初始化加载；失败可能影响 Legacy NCM/USB 数据通路 |
| `liblocal_hotspot_radio.so` | 19 | `network/LegacyHotspotNative` | 旧热点原生路径，需确认调用是否被能力检测隔离 |
| `liblwip_ncm_relay.so` | 19 | `network/LwipNcmNative` | 类初始化和 native 调用对 `UnsatisfiedLinkError` 有捕获，需验证降级是否完整 |
| `libopus_microphone.so` | 19 | `media/NativeOpusDecoder`、`media/NativeOpusEncoder` | 音频编解码路径，需检查是否可选加载 |
| `libspeex_echo.so` | 19 | `media/SpeexEchoCanceller` | 回声消除可考虑禁用降级，避免阻断主会话 |
| `libusb_host_compat.so` | 19 | `network/LegacyTunBlocking`、`transport/LegacyUsbHostNative` | 有静态初始化加载点，需确认目标 USB 路径及错误隔离 |
| `libxcertplay_chacha.so` | 19 | `airplay/NativeChaCha` | 加密算法实现；不能简单禁用，需验证替代实现和 TLS/会话安全 |
| `libxcertplay_i2c.so` | 19 | `transport/NativeLinuxI2cBridge` | I2C/仪表类功能，优先隔离为可选能力 |

**审计结论：** 9 个库全部为 ARM ELF32，ABI 与目标车机 `armeabi-v7a` 匹配；其中 8 个的 Android NDK 平台 note 为 API 19，1 个为 API 16。API 19 note 表示库以 API 19 平台头文件/目标构建，不能单凭 note 断言一定无法在 API 17 加载；必须结合未定义符号和目标设备 linker 实测。尤其应优先检查静态初始化中直接调用 `System.loadLibrary` 的类，避免可选模块加载失败导致整个功能路径崩溃。

### 对热点安全分析的补充

源码中确实存在 `HotspotJoinTransaction`、`HotspotJoinJournal`、`HotspotNetworkSnapshot` 等事务/快照类，但它们属于热点加入/连接流程，不能据此认为 ROOT hostapd 配置已有备份保护。

对 `network/HostapdHotspotManager` 的启动与 teardown 代码复核发现：
- 启动脚本将生成配置复制到 `/data/misc/wifi/hostapd.conf`，未见先备份原文件的动作。
- teardown 主要执行 stale hostapd/dnsmasq 清理、`ifconfig wlan0 down`，再通过 `WifiCtl ON` 恢复框架 Wi-Fi。
- 该 teardown 未见把原始 `hostapd.conf` 还原的逻辑。
- 部分逻辑会按热点状态复用并跳过 teardown；不能把“停止应用”理解为一定关闭热点或恢复系统配置。

因此之前提出的 ROOT 事务保护仍然成立。后续改造必须把“连接事务日志”和“系统 hostapd 配置备份/回滚”明确区分。

## 14. 当前阶段状态

- [x] 通过独立 GitHub Actions 对 APK 内 9 个原生库完成 ELF/API note/DT_NEEDED 审计。
- [x] 复核 JNI 加载位置并区分可选与关键路径的风险。
- [x] 复核 HostapdHotspotManager 对系统配置的覆盖与 teardown 行为。
- [ ] 在 API 17 实机加载各必要 JNI 库，记录 linker 输出。
- [ ] 建立 Honda 专用 HotspotBackend，先只读探测，再实现备份/回滚。
- [ ] 构建和签署适配 APK，并在 MY16ADA 车机进行安装与 CarPlay 实测。


### 13.1 关键 JNI 加载点的风险分级

结合 JADX 输出复核加载代码后，初步分级如下（仍需 API 17 设备运行验证）：

- **高风险、静态初始化直接加载：** `LegacyNcmNative`、`LegacyUsbHostNative`、`NativeLinuxI2cBridge`。如果类在 API 17 上被触发且库因系统符号缺失无法加载，可能抛出 `UnsatisfiedLinkError` 并影响调用链。
- **有显式降级捕获：** `LwipNcmNative` 的加载/创建/状态读取路径捕获 `UnsatisfiedLinkError`，但还需检查调用方是否正确处理“native unavailable”。
- **TLS 特殊路径：** `LockdownTlsEngineFactory` 对 `conscrypt_jni` 显式加载失败有记录，并尝试 Conscrypt Provider；该库的 API note 为 16，与目标 API 17 相对接近，但仍需验证 Conscrypt 与系统 TLS provider 的组合。
- **媒体可选性待确认：** Opus、Speex 类使用 API 19 note，需检查它们是否只在启用对应音频功能时加载，避免在主 Activity 或会话初始化时无条件触发。
- **加密关键路径：** ChaCha 原生库为 API 19 note，不能仅为兼容而禁用。应验证替代 Java 实现或在目标 API 17 上成功加载；不可绕过加密或降低协议安全性。

### 13.2 ROOT Shell 的 API 17 分支

`RootShell.waitForProcess` 仅在 SDK >= 26 时调用 `Process.waitFor(timeout, TimeUnit)`；低于 API 26 使用 `exitValue()` 轮询和短暂 sleep 的分支。因此这处新 API 已有运行时版本保护，API 17 路径不会直接调用该方法。仍需检查 `ProcessBuilder`、文件路径、shell 命令和 root 授权本身在目标固件上的实际行为。


## 15. ROOT 热点模块深度复核：新增关键安全风险

本轮直接从 GitHub Actions 反编译 Artifact 下载并检查 JADX 输出，同时对三个关键类进行本地模式复核：

- `network/HostapdHotspotManager.java`
- `network/HostapdConfig.java`
- `network/RootShell.java`

确认原始实现除覆盖系统配置外，还存在以下问题：

1. `killStaleForStartScript()` 与 `killStaleForTeardownScript()` 遍历全部 `/proc/[0-9]*`，凡 `comm` 等于 `hostapd` 或 `dnsmasq` 即执行 `kill -9`。没有验证进程由 DiPlay 启动，可能误杀车机系统服务或其他应用的服务。
2. 旧 `killStaleScript()` 也会按 `ps` 中进程名匹配后 kill，没有 app-owned PID 注册表。
3. 启动流程把临时配置直接复制到 `/data/misc/wifi/hostapd.conf`，未见覆盖前备份。
4. `removeGeneratedFilesScript()` 把系统配置路径 `/data/misc/wifi/hostapd.conf` 放进 `rm -f` 清理列表。这是系统文件，不得作为普通生成文件删除。
5. 启动/停止流程分别执行 `WifiCtl OFF` 和 `WifiCtl ON`，但所检查的管理器没有持久保存启动前 Wi-Fi 状态；恢复时无条件 ON 不等于恢复原状态。
6. `RootShell.writeFile(path, content)` 把 path 字符串直接拼接进 Shell 脚本。当前调用路径是否可控需要全量调用点复核，因此必须把路径限制为固定 allowlist。

这几项已加入新报告《ROOT_HOTSPOT_SAFETY_REDESIGN.md》，并新增 `tools/audit-hotspot-safety.py`，在 APK 每次重新反编译后自动检查这些源码特征。该扫描只做静态审计，不执行任何 ROOT 命令。

### 修复优先级

- **P0：** 禁止按进程名全局 kill；只允许终止本事务启动且 PID、进程启动时间、cmdline 均匹配的进程。
- **P0：** 从通用清理列表移除 `/data/misc/wifi/hostapd.conf`；覆盖前必须完成备份并校验。
- **P0：** 持久记录原始 Wi-Fi 状态、接口状态和原配置元数据，失败/崩溃后可恢复。
- **P1：** RootShell 文件路径 allowlist，避免将任意路径拼入 Shell。
- **P1：** 根据实机只读探测的真实接口名和可用二进制动态选择能力，不再默认 wlan0/hostapd/dnsmasq。

**重要：** 以上是对原始 APK 行为的静态发现，不代表已修改原 APK。当前还没有对车辆执行任何 ROOT 写操作，也没有生成修改版 APK。

## 16. 自动化审计阶段状态

- [x] 下载并读取原始 APK 反编译 Artifact 的真实源码。
- [x] 本地验证 6 项风险模式均能在原始源码中匹配。
- [x] 增加 GitHub Actions 自动热点安全扫描步骤。
- [x] 新建 ROOT 热点事务化改造设计文档。
- [ ] 等待最新 GitHub Actions 完成并检查审计报告产物。
- [ ] 建立可重建的 DiPlay 源码工程并集成事务管理器。
- [ ] API 17 实机验证备份、恢复、进程所有权与 Wi-Fi 状态恢复。
