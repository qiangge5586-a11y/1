# DiPlay 0.3.11 通信链路映射（基于反编译源码与 Smali 交叉核对）

分析对象：仓库根目录 `diplay0.3.11.apk`，SHA-256
`22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`。

分析输入：GitHub Actions 反编译产物 `diplay-0.3.11-decompiled`（artifact ID `13467082092`），JADX Java-like sources、APKTool Smali、Manifest 和资源。JADX 记录 104 个反编译错误；因此本文只把可由具体源码调用点支持的结论列为“已确认”，对复杂 Kotlin 控制流仍保留 Smali 复核要求。

## 1. 核心结论

原版并非单一“蓝牙连接 + Wi-Fi 热点”实现，而是至少包含两条不同的传输链：

- **有线**：USB Host / direct usbfs → iAP2 USBMUX → iPhone CDC-NCM → NCM 数据桥 → VPN/lwIP 网络承载 + iAP2 Lockdown/CarKit 控制。
- **无线**：先启动/准备车机热点 → 蓝牙 RFCOMM iAP2 bootstrap → 交换 Wi-Fi 配置和 CarPlay StartSession/transport 通知 → iPhone 转到 Wi-Fi 网络 → 通过单独的 iAP2 tunnel 控制流完成无线 CarPlay 控制；AirPlay 数据面走热点 IP 网络。

两条链最终由 `CarPlayController` 管理，但无线链不等同于有线 USB NCM 链。现有源码足以确认主要调用边和关键协议消息；没有设备运行日志，不能据此宣称真实 iPhone/车机互通已验证。

## 2. 有线 USB / iAP2 / NCM 调用链（源码调用点已确认）

### 2.1 标准 Android USB Host 路径

`CarPlayController`
→ 创建/使用 `IphoneUsbHost`
→ `discover()` / USB attach 与 permission receiver
→ `requestPermission()`
→ `openIap2UsbSessionAsync()`
→ `UsbManager.openDevice()`
→ 选择 CarPlay USB configuration
→ 定位 USBMUX interface 与 bulk IN/OUT endpoints
→ claim USBMUX interface（带重试/配置重新选择）
→ `Iap2UsbSession`。

`IphoneUsbHost.openIap2UsbSession()` 在 Android API < 21 时会先 sleep 400 ms；之后打开设备并共享 `UsbDeviceConnection`。它会选定 CarPlay configuration、定位 USBMUX 接口、确认 bulk endpoint 对，并执行接口 claim。USB session 是 iAP2 USBMUX 传输入口，不是普通 USB 文件传输。

### 2.2 Direct usbfs 备用路径

`CarPlayController.tryStartDirectAttempt()`
→ `SysfsUsbEnumerator` 找 iPhone 设备
→ `DirectUsbHost.openSession()`
→ `RawSharedUsbConnection.open()` 打开 usbfs 节点（日志明确标注 root grant allowed）
→ 读取 raw USB descriptors / 枚举 configuration
→ `IphoneCarPlayConfiguration.choose()`
→ 若当前没有 CarPlay configuration，执行 vendor re-enumeration control transfer：
  - requestType `0xC0`（十进制 192）
  - request `82`
  - value `0`
  - index `4`
  - 1-byte response，timeout 1000 ms
→ 释放旧连接，等待设备重新枚举（最长 30 秒）
→ 再次打开并解析 configuration
→ 建立 `Iap2UsbSession`
→ `CarPlayController.onDirectSessionConnected()`。

这条路径直接操作 usbfs，明显比只读 USB 设备枚举权限更高；不能直接并入当前被动扫描器。其 sysfs 路径、节点权限、USB 设备重枚举和 vendor control request 均需在目标 Honda 固件单独验证。

### 2.3 USBMUX → NCM

`CarPlayController.openNcm(vendorId, usbSession)` 的调用链可确认：

1. 从 `Iap2UsbSession.getCarPlayConfiguration()` 取得 CarPlay configuration。
2. `NcmFunctionDiscovery.find(configuration)` 查找 CDC-NCM 功能。
3. 发现条件包括 control interface class 2 / subclass 13；data interface class 10，且需要 bulk endpoint 对。
4. 从同一 USB session 调用 `retainConnectionForNcm()`，共享底层 `UsbDeviceConnection`。
5. 调用 `NcmUsbBridge.open$shared()`，选取 control/data alternate setting、claim 需要的 interface，使用 bulk IN/OUT 与可选 status endpoint。
6. `NcmUsbBridge` 解析 NTB/NTH16 数据帧；可见 NTH16 长度 12、read chunk 32 KiB、NTB 最大合理长度 1 MiB 等实现约束。

`NcmUsbBridge` 的异步 `UsbRequest` 读取只在真实 SDK >= 24 时启用；API 17 会走同步 bulkTransfer 读取分支。这是重要的旧系统兼容分支，但并不证明目标设备的 USB 驱动和 NCM 描述符符合预期。

### 2.4 iAP2 Lockdown / CarKit 与网络数据面

`CarPlayController.runStack(usbSession, ncm)` 继续执行：

`Iap2UsbMuxHost.open()`
→ `LockdownPairingClient`（读取/建立 pair record）
→ `LockdownCarKitClient.open()`（打开 `com.apple.carkit.service`）
→ `Iap2Session.open(..., "wired", ...)`（CSM 控制通道）
→ `attachVpn(ncm, hostMac)`
→ `CarPlayVpnService`。

`CarPlayVpnService` 中可见 `new LwipNcmBridge(ncm, hostMac, linkLocal, ...)`；`LwipNcmBridge` 启动 NCM RX/TX 线程，把 NCM frame 和 lwIP native 网络栈连接起来，并包含 IPv6 邻居发现/路由通告及 mDNS 相关处理。之后 `Iap2WiredControlClient` 负责有线 CarPlay 控制、MFi authentication 和 StartSession；AirPlay TCP 到达情况另有 watchdog/状态处理。

所以 NCM 是有线 CarPlay 网络数据面，不只是一个“热点桥接”名称。

## 3. 无线 Bluetooth RFCOMM / iAP2 / Wi-Fi handoff 调用链（源码调用点已确认）

### 3.1 RFCOMM 连接

`CarPlayController.runWireless()` 先准备无线热点和 Wi-Fi 网络信息，再选择配对 iPhone。设备选择逻辑参考 bonded devices、当前已连接设备、用户保存的 Bluetooth address，以及可用的 Hybrid/USB Bluetooth adapter。

RFCOMM 服务 UUID：
`00000000-deca-fade-deca-deafdecacafe`
（定义在 `CarPlayController.IAP2_IPHONE_UUID`；`UsbBtIdentity` 中亦定义同一 iAP2 iPhone UUID。配件 UUID 为 `00000000-deca-fade-deca-deafdecacaff`，CarPlay UUID 为 `ec884348-cd41-40a2-9727-575d50bf1fd3`。）

`AndroidBluetoothAdapter.openRfcommStream()` 的 socket 策略：
1. `createRfcommSocketToServiceRecord(uuid)`（secure RFCOMM）；
2. 失败后等待 100 ms，再试 `createInsecureRfcommSocketToServiceRecord(uuid)`；
3. 再失败时反射调用隐藏方法 `createInsecureRfcommSocket(1)`；
4. 连接在独立 daemon thread 执行，并通过 CountDownLatch 等待；默认连接超时 10 秒；
5. 成功后包装成 `BluetoothRfcommDuplexStream`，其 reader thread 持续读取 InputStream，send 走 OutputStream，close 关闭 socket。

Controller 层最多做 4 次无线 RFCOMM 启动尝试，并有连接快照和失败诊断。反射隐藏 API 是旧设备兼容风险点，应保留为隔离 fallback，不可假设所有 ROM 都允许。

### 3.2 RFCOMM 上的 iAP2 bootstrap

`CarPlayController` 将 RFCOMM byte stream 交给：
`Iap2Session.openWireless(stream, "wireless-rfcomm", ...)`
→ `Iap2CsmChannel.openWireless()`
→ `Iap2WirelessControlClient.run()`。

`Iap2WirelessControlClient.run()` 依次：
- 检查 identification 配置确实声明 wireless transport；
- 执行 iAP2 identification；
- 执行 MFi authentication；
- 发送 iAP2 wired-control subscriptions；
- 进入消息循环，转发 location / vehicle-status 数据（若启用）。

源码中可确认的关键消息 ID（十进制）：
- `17152 / 0x4300`：CarPlay Availability；收到后发送 CarPlay StartSession。
- `17153 / 0x4301`：CarPlay StartSession（发送消息）。
- `19981 / 0x4E0D`：Wireless CarPlay Update；消息体第 0 字段表示 availability 0/1。
- `19982 / 0x4E0E`：Device Transport Identifier Notification；解析 Bluetooth/USB transport identifiers。
- `22274 / 0x5702`：Request Accessory Wi-Fi Configuration。
- `22275 / 0x5703`：Accessory Wi-Fi Configuration。

`Iap2WirelessMessages.accessoryWiFiConfiguration()` 把 BSSID（可选）、SSID、passphrase、security type 和 channel 编码到 Wi-Fi configuration 消息。控制器会按消息状态在 transport notification 前后发送配置；StartSession 在需要时每 4 秒重发，最多 6 次。该流程解释了蓝牙为何先于 Wi-Fi handoff：RFCOMM 用于发现/协商无线 CarPlay，并向 iPhone 提供热点参数。

### 3.3 热点与无线 tunnel 控制通道

热点后端由 `CarPlayController.startWirelessHotspot()` 按配置模式选择，源码中分支包括：
- Wi-Fi Direct（SDK >= 28）
- LocalOnlyHotspot（SDK >= 26）
- Existing Wi-Fi
- Manual hotspot
- Hostapd hotspot。

`CarHotspotTethering` 还会在配置要求时先尝试启用车机热点，并在特定失败状态下允许 manual fallback。热点准备完成后，控制器解析本机热点 IP、等待客户端连接，并监视首个 AirPlay TCP 活动。

无线链路的后半段不是“收到 type-130 就直接得到 iAP2 字节流”。`CarPlayMediaEngine.onDataStream()` 处理 AirPlay DataStream SETUP：当 `clientTypeUUID=E9459FD0-BCAD-4C45-820F-1E72447EF2F2` 且存在 AirPlay shared secret 与 seed 时，使用 HKDF-SHA512 派生 32 字节读取密钥，创建 `IapTunnel` TCP listener 和 `AirPlayIapTunnelStream`，然后返回 `type=130`、`streamID=1`、`dataPort`。iPhone 连接该 TCP 端口后，`IapTunnel` 解密/解析数据并把 `comm` 包体送入 `AirPlayIapTunnelStream`；注册的 handler 才调用 `startWirelessTunnelControl(stream)` → `Iap2Session.openTunnel(stream, "wireless-tunnel", ...)` → `Iap2WirelessControlClient.run(...)`。因此 type-130 是 AirPlay DataStream SETUP 的类型标识，TCP IAP carrier 和其上的 iAP2 tunnel session 是不同层次。详见 [AirPlay type-130 与加密 IAP tunnel 源码追踪](AIRPLAY_TYPE130_IAP_TUNNEL_SOURCE_TRACE.md)。

此处应区分两条通道：
- `wireless-rfcomm`：蓝牙上的 bootstrap/control；
- `wireless-tunnel`：后续独立 iAP2 tunnel control stream；
- AirPlay 媒体/网络数据面：iPhone 加入热点后经 Wi-Fi IP 网络建立 TCP/RTSP 等连接，由 VPN/网络服务处理。

本次源码检查确认了两条 iAP2 session 的打开位置及 tunnel 接收处理，但未在没有运行日志的情况下确认 type-130 stream 的底层创建时序、所有 handoff 竞态分支或特定 iPhone 型号的协商差异。

## 4. 关键模块证据表

| 模块 | 直接可确认的实现点 | 证据 |
|---|---|---|
| `CarPlayController` | `tryStartDirectAttempt`、`onDirectSessionConnected`、`openNcm`、`runStack`、`runWireless`、`startWirelessTunnelControl` | 方法级调用点 |
| `IphoneUsbHost` | `openIap2UsbSession`、USB permission/attach/detach receivers、USBMUX claim retry | 方法级调用点 |
| `DirectUsbHost` | sysfs/usbfs open、raw descriptor read、vendor re-enumeration、session build | 方法级调用点 |
| `IphoneUsbMatcher` | Apple vendor ID 1452 (0x05AC) 或明确 allowlist VID/PID 匹配 | 方法级实现 |
| `NcmFunctionDiscovery` | CDC class/subclass、data interface 和 bulk endpoints 发现 | 方法级实现 |
| `NcmUsbBridge` | NCM alternate setting、claim、bulk read/write、NTB framing、resync/watchdog | 方法级实现 |
| `LwipNcmBridge` | NCM RX/TX 与 lwIP native stack、IPv6/ND/mDNS | 方法级实现 |
| `AndroidBluetoothAdapter` | paired-device inventory、RFCOMM secure/insecure/hidden fallback、socket connect、stream wrapper | 方法级实现 |
| `BluetoothRfcommDuplexStream` | dedicated reader thread、queued reads、serialized writes、socket close | 方法级实现 |
| `Iap2WirelessControlClient` | identification、MFi、subscriptions、0x4300/0x4301/0x4E0D/0x4E0E/0x5702/0x5703 | 消息 ID 与状态机 |
| `CarHotspotTethering` / hotspot managers | 自动热点协调、manual/hostapd/Wi-Fi Direct/LocalOnly/Existing Wi-Fi 后端 | 分支调用点 |

## 5. API 17 / Honda 移植风险（本轮新增观察）

1. 标准 USB Host API 自 API 12 起存在；目标 API 17 在 API 层面具备基础 USB Host 入口，但具体权限、USB host 供电、描述符和 Honda kernel driver 仍需实机验证。
2. `IphoneUsbHost` 在 SDK < 21 有专门 400 ms 等待；这是原版针对旧 Android USB 时序的兼容提示。
3. `NcmUsbBridge` 仅在 SDK >= 24 使用异步 `UsbRequest` read queue；API 17 走同步 bulkTransfer 路径。同步路径的性能、超时和 USB driver 行为尚未实测。
4. `DirectUsbHost` 直接打开 sysfs 对应 usbfs 节点并发 vendor control transfer，需要 root/节点权限和特定内核行为，不能等同于 Android 公共 USB Host API。
5. Bluetooth RFCOMM 的标准 secure/insecure socket API 在旧 Android 已有；但最后的反射 `createInsecureRfcommSocket(1)` 属隐藏 API，ROM 可能拒绝或实现差异。
6. Wi-Fi Direct 分支只在 SDK >= 28 进入，LocalOnlyHotspot 只在 SDK >= 26 进入；API 17 不可使用这两个分支。API17 适配要走原有 car hotspot、manual/existing Wi-Fi 或经验证的 hostapd backend，不能直接照搬现代热点 API。
7. 原 APK 有 8 个原生库标记 Android API 19，目标 API 17；其中 NCM/lwIP/蓝牙等 native JNI 路径需要逐个做 ELF 动态依赖和符号检查。不能通过改 ELF note 掩盖真实最低 API。
8. 原版热点后端仍有高风险：全局 kill hostapd/dnsmasq、直接覆盖 hostapd 配置、teardown 无条件开启 Wi-Fi、ROOT 命令路径拼接。必须继续用事务化备份、进程身份校验和回滚方案替代。

## 6. 对当前适配工程的影响

当前 `DiPlay/android` 是独立的中文诊断/兼容验证壳，不是原版 CarPlay 实现。它目前：
- USB 只读枚举设备描述，不 claim interface、不打开 endpoint；
- Bluetooth 只读检查适配器状态，不启停、不扫描、不配对、不连接；
- 热点事务模型没有真实 su executor；
- 不启动 hostapd/dnsmasq、不改 Wi-Fi 系统配置。

不能把当前 APK 构建成功等同于上述原版通信链路已移植。后续应按以下顺序实现和验证：API17 兼容基础 → USB descriptor fixture → iAP2 USBMUX session（仅在明确授权设备测试环境）→ NCM parser/codec 离线测试 → lwIP/NCM 网络数据面 → Bluetooth RFCOMM iAP2 bootstrap → wireless tunnel/control → AirPlay/VPN 集成。

## 7. 下一轮任务

- [ ] 从 Smali 对 `DirectUsbHost` 的 raw usbfs 权限获取和 vendor re-enumeration 做逐指令复核。
- [ ] 对 `NcmUsbBridge` 与 `Ntb16Codec` 建立 NTB16 fixture 测试，覆盖边界长度、坏 header、序号回绕、resync。
- [ ] 追踪 type-130 stream 产生点和 wireless handoff 完整状态迁移。
- [ ] 检查 `CarPlayVpnService` 的 VPN 建立权限、native lwIP 加载和 API17 兼容。
- [ ] 对所有 `.so` 做依赖符号和最低 API 静态审查。
- [ ] 只在 API17 模拟器/授权测试设备做运行验证；ROOT 热点写操作另行单独授权。

## 8. 结论

现已从真实反编译源码确认：有线链路以 USBMUX+iAP2 为控制/配对入口，以 CDC-NCM+lwIP/VPN 为网络数据面；无线链路先以蓝牙 RFCOMM+iAP2 交换身份、CarPlay 可用性和热点 Wi-Fi 配置，再建立 iAP2 tunnel control，并由 Wi-Fi IP 网络承载 AirPlay。下一阶段应把精力从泛化的类名梳理转到 API17 的 NCM/USB/BT 兼容和离线协议测试。没有在 MY16ADA 车机上运行或实车验证。
