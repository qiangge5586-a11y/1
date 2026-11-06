# DiPlay 0.3.11 媒体线程与队列静态审计

审计对象：原版 `diplay0.3.11.apk` 的 APKTool Smali（反编译 Run #4，Artifact `13467082092`）。
审计日期：2026-11-07。
目标环境：Honda MY16ADA，Android 4.2.2 / API 17，ARMv7，约 0.94 GB RAM（检测报告值）。

> 本文是静态源码审计，不是车机运行时 profiling。下述队列容量是代码配置/边界，不代表实际占用或实测延迟。

## 1. 视频接收与解密

证据：`airplay/ScreenStream.smali`。

- `ScreenStream.run(Socket)` 在专用 `airplay-screen` 线程读取 128-byte header 和 body；body size 上限为 8 MiB。
- `listen()` 启动独立 `airplay-screen-decrypt` daemon worker。
- 构造函数建立容量为 10 的 `LinkedBlockingQueue`。
- `onMessage()` 对帧执行最多 30 ms 的 timed offer；失败时增加 droppedFrames 并通知 listener。
- decrypt worker 从队列取出帧，执行 `ScreenCodec.decryptFrame()`，再调用 listener 的 codec/config/frame 回调。
- `close()` 关闭 socket/server、interrupt 两个线程并清空队列。

判断：
- 已有有界帧队列、接收/解密分线程和丢帧计数，不能简单把队列改成无界或随意丢弃队头。
- 风险点：队列按帧数限制，不按累计字节限制；单帧允许到 8 MiB，理论上 10 帧可形成很高的内存上界（不等于实际会达到 80 MiB）。目标设备可用 RAM 有限，值得加入队列累计字节观测。
- 30 ms offer 只在队列满时发生；需用真实 queue-full 计数和 offer wait duration 判断它是否形成持续接收阻塞。

## 2. 视频解码队列与 MediaCodec

证据：`media/VideoDecodeQueue.smali`、`media/VideoDecoder.smali`。

- 默认 VideoDecodeQueue 上限为 60 帧和 8 MiB；内部 jobs 使用 LinkedBlockingQueue，另有 pendingFrames/pendingBytes 记账。
- 帧超出帧数或字节边界时，清除待处理帧并加入 Resync job；单帧本身超过 maxBytes 时丢弃。
- VideoDecoder 有独立工作线程；它按队列 job 顺序处理 config/frame/surface/detach/reverse 等事件。
- 帧年龄超过 800 ms 时：若近期仍有输出，则丢弃过期帧继续喂流；若输出停滞超过约 1.5 s，则清理帧队列并触发 decoder recovery。
- `drainOutput()` 使用非阻塞 `dequeueOutputBuffer(..., 0)`，再按 Surface 状态调用 `releaseOutputBuffer(index, renderTimeNs)` 或 `releaseOutputBuffer(index, render)`。
- 解码入口会把 NAL 单元转换为 Annex-B，并通过 VideoReferenceChain 检查参考帧链；不接受的帧会丢弃并按策略请求 keyframe。

判断：
- 这套设计已经有帧数+字节双重上限、陈旧帧清理、Resync 和参考帧链保护。不能只把 60 帧盲目调低，否则可能频繁触发重同步并造成花屏/黑屏。
- 60 帧是硬上限而非目标缓存时长；真正排队延迟要结合帧时间戳、VideoStats decode latency 和 stale drop 指标测量。
- MediaCodec drain 使用 timeout=0，单次 drain 不等待新输出；这本身不是阻塞主线程的证据。

## 3. 音频接收、缓冲与输出

证据：`airplay/BufferedAudioStream.smali`、`media/AudioRenderer.smali`。

- BufferedAudioStream 使用独立 `airplay-buffered-rx` 与 `airplay-buffered-feed` daemon threads。
- 输入通过 8 KiB BufferedInputStream；接收侧维护 queue/fed 两个 ArrayDeque，并以锁保护。
- 接收缓冲限制包括 8 MiB queued bytes 与按采样率计算的最多 120 秒 frame 上限；缓冲达到上限时 receiver 在 lock 上 wait，feed/retire 后 notifyAll。这是有界背压，不是无界堆积。
- feed 每轮最多按 1024 samples 记账，并受 1 秒 lead gate 和 20 ms wait/poll 约束；MediaSink 回调在 lock 内执行，因此必须继续确认回调只做快速入队。
- AudioRenderer 使用独立 renderer thread，输入 LinkedBlockingQueue 容量 192；队列满时 drop newest 并计数，不阻塞网络接收线程。
- AudioTrack.write 在 AudioRenderer 的 PCM 输出路径执行；调用会记录 write 耗时，并处理 partial/zero/error write。不是 UI thread 路径的证据。

判断：
- 音频路径已把网络接收、buffer feed、renderer/AudioTrack write 分开，并限制了缓冲规模。
- 需进一步确认 MediaSink.onAudioRtp() 是否只做 AudioRenderer.submit() 快速入队；目前 AudioRenderer.submit() 是非阻塞 offer，满队列时丢新包，未发现它同步等待 AudioTrack.write。
- 不应把 AudioTrack.write 改为异步双重队列；现有 renderer worker 已是单独输出 owner。

## 4. 本轮适配层 UI 检查

文件：`DiPlay/android/app/src/main/java/com/shilapi/xcertplay/MainActivity.java`。

已提交改动：
- USB 只读枚举改为单线程诊断 Executor 执行；主线程只显示“正在扫描”和最终结果。
- 蓝牙只读检测改为同一后台 Executor 执行。
- 按钮在任务期间禁用，完成后恢复；异常转为可见诊断文本。
- Activity 销毁时设置生命周期标记、shutdownNow Executor，并关闭 Presentation，避免后台结果回写已销毁 Activity。
- ROOT 只读检查本来已在独立 Thread 执行；本次不改变任何 ROOT 命令或系统写入行为。
- Presentation 的创建、show/dismiss 和 DisplayManager 查询继续留在 UI 线程；没有将窗口操作搬到后台线程。

## 5.1 ScreenStream 指标辅助类进展（2026-11-07）

- 新增/整理 `DiPlay/compat/airplay/ScreenStreamQueueMetrics.java`：累计排队字节、字节/深度 high-water、offer/dequeue/drop 数量、offer 等待总时长和最大值。
- 指标只在显式 `snapshot()` 时复制为不可变快照；不逐帧写 logcat，不改变队列 admission、排序、超时或丢帧策略。
- `ScreenStreamQueueMetricsTest` 已改为直接测试生产辅助类，而非在测试里复制一份独立记账实现；覆盖排队字节、high-water、drop、wait 统计、dequeue 和 underflow clamp。
- CI 已执行该测试：Run `42250410045`，`ScreenStreamQueueMetricsTest PASS: 9 checks`，整组 iAP2/AirPlay 合同测试通过。
- **重要集成边界：当前 merge patch 脚本尚未向原版 `ScreenStream.smali` 插入这些指标调用。** 因此这只是通过单测的观测辅助类，不代表 APK 运行时已经采集 ScreenStream 队列数据。接下来必须取得本次构建解包出的原版 Smali，核实准确方法/寄存器/队列元素大小，再补丁并反编译复核，不能仅因类被打进 classes5.dex 就宣称已生效。

## 6. 建议的下一步（先测量，再调整）

P0（只增加观测，不改变协议）：
1. ScreenStream：累计排队字节 high-water、queue depth high-water、offer wait 总时长/最大值、drop count。
2. VideoDecodeQueue：pending frame/bytes high-water、Resync 次数、stale drop/recovery 次数。
3. AudioRenderer：队列 high-water、drop count、AudioTrack write max/total duration、partial/zero write。
4. 以会话级低频 snapshot 输出指标，避免 per-frame logcat。

P1（有真实样本后再决定）：
- 若 ScreenStream 的 queue bytes 经常高，优先研究按字节限额与安全丢帧策略；保留 codec reference-chain 约束。
- 若 VideoDecodeQueue 反复 Resync，分析 MediaCodec input/output stall 与 Surface detach，而不是先增大队列。
- 若 AudioRenderer queue 持续逼近 192 或 AudioTrack write 长尾明显，先定位设备音频 HAL / route / buffer plan，再调整缓冲参数。

禁止事项：
- 不改 AirPlay 加密、frame nonce/counter、iAP2、ACK/重传、MFi 认证。
- 不改变视频 NAL/reference-chain 语义或静默删除关键帧。
- 不改音频采样格式、RTP 时序、同步锚点或音频路由。
- 不执行 ROOT 热点写入。

## 7. 结论

原版媒体引擎已经有多层独立线程和有界队列，当前最有价值的优化方向不是再增加线程或盲目缩小队列，而是把 ScreenStream 的“按帧数但无累计字节上限”作为首要观测点，并把视频解码/音频输出的现有 high-water 与耗时指标收集齐全。只有拿到目标车机实测数据，才适合做参数调整。
