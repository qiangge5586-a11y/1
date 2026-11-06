# iAP2 Android 传输层接入审计（Smali 源码核对）

日期：2026-11-06  
对象：DiPlay 0.3.11 原始 APK；反编译产物 Actions artifact `13478968581`（`iap2-smali-source.zip`）。  
边界：本文只记录反编译证据与当前工程差距，不代表已接入或实机验证。

## 1. 原版真实传输通道（已由 Smali 确认）

核心类：`com.shilapi.xcertplay.transport.Iap2LinkChannel`。

- 构造时保存 `BlockingDuplexByteStream`、链路配置、协商方向与回调，并启动单独的 worker thread。
- `runPump()` 在 worker 内创建唯一的 `Iap2LinkEngine` 实例；引擎的 start/feed/定时推进/命令处理/事件处理/输出 flush 均由该泵串行驱动。
- 每轮先 `drainCommands(engine)`：在 engine writable 时从受锁保护的 FIFO 命令队列取出命令，再调用 `engine.sendSession(sessionId, payload, now)`。
- `flush(engine)` 调用 `engine.takeOutput()`，有字节时一次性传给 `underlying.send(bytes)`。
- 输入通过 `underlying.recv(8192, waitMillis)` 读取；最大读取块为 8192 字节，轮询等待被限制在 1–100 ms，并结合引擎下一个 deadline 调整。
- 非发起端在 800 ms 无数据后最多触发一次同步 fallback；无线 peer 在链路 ready 前 12,000 ms 静默则失败。握手进度超时由独立的 10,000 ms 参数控制。
- 非空输入调用 `engine.feed(received, now)`；流结束按链路是否已 ready 区分提前断开和正常 EOF，再进入终止清理。
- `recvControl(timeout)` 从受锁保护的 FIFO controls 队列取数据，并按字节数扣减队列记账；`sendControlAwaitCapacity(payload, timeout)` 在容量条件满足后将命令入队并等待 worker 消费。

原版队列边界（Smali 静态常量）：

| 项目 | 上限 |
|---|---:|
| pending commands 数量 | 64 |
| pending command 总字节 | 1,048,576 |
| pending controls 数量 | 64 |
| pending control 总字节 | 1,048,576 |
| 单次底层 recv | 8,192 bytes |
| 单个 link control payload | 65,525 bytes |

这些边界分属不同队列，不应合并成一个“256帧”限制。原版 link engine 的 encoded output 自身另有 1 MiB 字节预算。

## 2. CSM 层调用边（已由 Smali 确认）

`Iap2CsmChannel` 持有 `Iap2LinkChannel`：

- `recv(timeout)` 通过 `link.recvControl(timeout)` 取得已经由 link engine 重组的 control payload，再交给 CSM framer。
- `send(frame, timeout)` 将 CSM frame 编码/拆分为 link control chunks，并通过 `link.sendControlAwaitCapacity(chunk, remainingDeadline)` 发送。
- `awaitReady(timeout)` 委托 link channel，并读取 peer 最大 control payload。
- CSM 层不能把 8 KiB raw stream read limit 当作 CSM frame 输入上限；raw stream read、link payload、CSM framing 是三层不同边界。

## 3. 当前仓库与 Android APK 的真实差距

当前 `DiPlay/android/app/build.gradle` 的 sourceSets 只纳入：
`src/main/java`、`compat/hotspot`、`compat/usb`、`compat/bluetooth`、`compat/ncm`。

它没有纳入 `compat/iap2`。Android app 源码目前只有诊断 Dashboard 的 `MainActivity.java`，没有：

- `BlockingDuplexByteStream` 的 Android RFCOMM / USB / TCP 实现；
- 生产版 `Iap2LinkChannel` / `Iap2LinkEngine`；
- 生产版 `Iap2CsmChannel` / CSM framer；
- 从 Bluetooth RFCOMM 或 AirPlay IAP TCP tunnel 创建会话并交给 CSM 的生命周期管理。

`compat/iap2` 当前全部是 Java 7 离线 codec / state models / tests。即使把目录加入 APK sourceSets，也只会把模型编译进 APK，不会自动产生 socket I/O、worker pump、线程安全队列或真实握手。因此本阶段不把离线模型伪装成生产链路，也不直接替换原版引擎。

## 4. 建议的生产接入顺序（必须逐层验证）

1. **Transport adapter**：先定义 API 17 可用的阻塞式双向字节流契约；输入必须区分 timeout、EOF、关闭和 I/O error，输出必须保证完整写入或抛错。分别实现 RFCOMM 和后续 IAP TCP carrier，禁止把两种承载混成同一个握手阶段。
2. **Single-owner pump**：由单一 worker 独占 link engine；所有入站 bytes、定时器推进、出站命令、编码输出都经此线程串行化。业务线程只通过有界 FIFO command/control 队列交互。
3. **Source-parity engine**：先把已审计模型与原始 Smali 的状态转移、deadline、事件顺序和队列边界逐项对照；没有覆盖的行为不得切换生产路径。
4. **CSM bridge**：只有 link READY 后才开放 CSM；CSM framer 接收 link reassembly 后的 control payload，按 peer max payload 拆分发送，并共用绝对 deadline。
5. **Wireless handoff**：RFCOMM bootstrap、Wi-Fi 配置通知、AirPlay type-130 SETUP、加密 IAP TCP tunnel 和 tunnel 内 iAP2 CSM 是不同阶段；每个阶段独立记录成功/失败，不能把 RFCOMM ready 等同于 tunnel ready。
6. **Hardware gate**：先在 Android 4.2.2/API 17 编译和运行时验证，再用受控 iPhone/车机日志验证；不在未确认的情况下触发 ROOT 热点写入。

## 5. 当前可执行结论

- 已完成：原版 link pump 的读写方向、线程所有权、命令/控制队列边界、CSM 调用边源码核对。
- 未完成：生产 transport adapter、生产 link engine port、CSM channel port、RFCOMM 与 tunnel 实际接入、Android 4.2.2 runtime 和车机实测。
- 当前 iAP2 CI 的 2,323,738 项检查属于 Java 7 离线模型检查，不是 APK 运行测试，也不是真实设备握手。

后续最小风险实现应先新增独立的 API 17 双向流 adapter 与 pump contract 测试，再迁移生产 link engine；不能先把模型的 `drainWireFrames()` 直接连到 socket，因为它尚未完整复刻原版 worker 的定时、阻塞、事件和关闭语义。
