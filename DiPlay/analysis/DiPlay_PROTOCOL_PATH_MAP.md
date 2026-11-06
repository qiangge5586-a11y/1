# DiPlay 0.3.11 通信链路映射（静态分析阶段）

分析对象：仓库根目录 `diplay0.3.11.apk`，SHA-256
`22f61878e0e7e78a3fb9c2e2051881d3cc2e4b0232f4c3f13090ace04a966fb1`。

> 本文是基于 APK 反编译摘要和类/模块结构的静态架构映射，不是运行时抓包，也不是完整的逐方法调用图。JADX 曾报告 104 个反编译错误；复杂分支应以 Smali/DEX 复核。所有链路边均标注证据等级。

## 1. 已确认的模块边界

| 模块/类 | 静态分析可确认的职责 | 证据等级 |
|---|---|---|
| `orchestration/CarPlayController` | 会话编排；管理有线/无线连接、启动状态、重试与诊断 | 类级结构已确认 |
| `transport/IphoneUsbHost` | iPhone USB Host 传输入口 | 类名与模块归属已确认；逐方法时序待核 |
| `transport/DirectUsbHost` | 直接 USB Host 传输路径 | 类名与模块归属已确认；设备匹配规则待核 |
| `transport/NcmUsbBridge` | NCM 网络桥接 | 类名与模块归属已确认；网络参数/数据方向待核 |
| `BluetoothRfcommDuplexStream` | 蓝牙 RFCOMM 双向字节流 | 类级职责已确认；配对与连接发起方待核 |
| `network/CarHotspotTethering` | 车机热点协调入口，含旧版 Wi-Fi API 路径 | 静态代码结构已确认 |
| `network/HostapdHotspotManager` | ROOT hostapd/dnsmasq 热点后端 | 静态代码结构已确认 |
| `network/ManualHotspotManager` | 手动/兼容热点后端 | 静态代码结构已确认 |
| `network/RootShell` | 通过 su 执行命令、读取输出及文件操作 | 静态代码结构已确认 |

## 2. 逻辑链路（高层）

### 有线 CarPlay（待逐方法验证）

`USB Host 设备枚举/匹配`
→ `IphoneUsbHost 或 DirectUsbHost`
→ `iAP2/MFi 会话与传输层`
→ `CarPlayController 会话编排`
→ 媒体/显示/输入等会话功能。

其中 USB Host、iAP2/MFi、CarPlayController 和媒体/显示模块均存在于 APK；箭头表达模块间预期协作关系，**并不代表已经取得完整方法级调用证据**。下一步需要从 Smali 对构造器、接口回调、Intent/Service 启动和数据流逐条反向追踪。

### 无线 CarPlay（待逐方法验证）

`蓝牙 RFCOMM/配对相关通道`
→ 无线会话建立/协商（具体消息与触发点待核）
→ `CarHotspotTethering`
→ 旧版 Wi-Fi AP 或 ROOT hostapd 后端
→ IP 网络承载/NCM 或其他网络传输路径
→ `CarPlayController`。

蓝牙 RFCOMM、热点管理、NCM 网络桥接和会话编排类均已识别；但现有可访问证据不足以确认蓝牙消息是否直接触发热点、是否存在独立的配对广播接收器、NCM 与热点是否串联或互为替代后端。不能把该示意图当作已证实的时序图。

## 3. 已知高风险实现与移植要求

1. 原热点实现可能按进程名全局终止 hostapd/dnsmasq，必须改为本事务拥有进程的 PID、启动时间和 cmdline 三重校验。
2. 原实现覆盖/清理 `/data/misc/wifi/hostapd.conf`，移植前必须先备份并校验，失败时恢复；不得将系统配置当普通临时文件删除。
3. 原实现存在关闭 Wi-Fi 后 teardown 无条件重新开启的行为；新实现必须恢复操作前状态，而非固定开启。
4. `RootShell` 路径参数必须固定 allowlist，禁止将未经验证的路径拼接进 Shell。
5. BYD 专用权限、包查询和 Cluster 接口必须从 Honda 通用路径隔离。
6. 八个原生库的 ELF Android 平台标记为 API 19；目标车机 API 17，需逐个验证加载和动态符号，不能伪改 ELF note。

## 4. API 17 与当前验证壳的边界

当前独立 APK 的 USB 和蓝牙功能均为只读探测：
- USB：通过 Android USB Host API 读取设备描述，不打开设备、不 claim interface、不读写 endpoint。
- Bluetooth：读取适配器存在状态、state、enabled 和可用时的设备名称；不启用/关闭蓝牙、不扫描、不配对、不连接。
- 热点事务类目前只做纯 Java 状态模型、备份记录、日志和模拟测试；没有真实 su 执行器。
- APK 的 CI 成功仅证明构建、打包及静态元数据检查，不证明 API 17 运行、MY16ADA 设备行为或 CarPlay 互通。

## 5. 下一轮要完成的证据采集

- [ ] 以 Smali/DEX 搜索所有对 `IphoneUsbHost`、`DirectUsbHost`、`NcmUsbBridge` 的构造、调用和回调引用，形成方法级引用表。
- [ ] 追踪 `BluetoothRfcommDuplexStream` 的创建、socket connect、输入/输出流读写和关闭路径。
- [ ] 追踪 `CarPlayController` 对有线/无线入口的分派条件、状态机与重试策略。
- [ ] 追踪热点后端选择条件、热点状态回调与 NCM 初始化/释放的真实先后关系。
- [ ] 标记每条路径调用的 API 级别、隐藏 API、权限和原生 JNI 依赖。
- [ ] 使用 API 17 模拟器/设备验证类加载和只读检测；目标车机验证须另行安排，且 ROOT 写操作需单独授权。

## 6. 当前结论

目前可以可靠确认 DiPlay 同时包含 USB/iAP2、蓝牙 RFCOMM、热点管理、NCM 网络桥接和 CarPlay 会话编排模块；尚不能仅凭现有报告证明无线 CarPlay 的完整协商时序或这些模块之间的精确方法级调用关系。应继续以 Smali/DEX 引用和运行时日志补齐证据，而不是凭类名直接实现猜测的协议流程。
