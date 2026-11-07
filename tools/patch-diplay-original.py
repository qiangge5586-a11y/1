#!/usr/bin/env python3
"""Apply the DiPlay 0.3.11 Honda test-fork safety deltas to an Apktool tree."""
import os
import shutil
import sys


def replace_once(path, old, new, label):
    with open(path, "r", encoding="utf-8") as source:
        content = source.read()
    count = content.count(old)
    if count != 1:
        raise SystemExit("%s: expected exactly one patch anchor, found %d" % (label, count))
    with open(path, "w", encoding="utf-8") as target:
        target.write(content.replace(old, new, 1))


def replace_once_any(path, candidates, label):
    """Apply the first exact-one anchor; useful across Apktool debug-local variants."""
    with open(path, "r", encoding="utf-8") as source:
        content = source.read()
    for old, new in candidates:
        count = content.count(old)
        if count == 1:
            with open(path, "w", encoding="utf-8") as target:
                target.write(content.replace(old, new, 1))
            return
    counts = [content.count(old) for old, _new in candidates]
    raise SystemExit("%s: no unique patch anchor; candidate counts=%s" % (label, counts))


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch-diplay-original.py APKTOOL_DECODED_DIR")
    root = sys.argv[1]
    persistence = os.path.join(root, "smali/com/shilapi/xcertplay/AirPlayPersistence.smali")
    host = os.path.join(root, "smali/com/shilapi/xcertplay/CarPlayHostActivity.smali")
    choices = os.path.join(root, "smali_classes3/com/shilapi/xcertplay/settings/ConnectionSettingsSection.smali")

    target = "Lcom/shilapi/xcertplay/orchestration/MfiTarget;"
    replace_once(
        persistence,
        "    sget-object v2, %s->LOCAL:%s\n\n    :cond_2" % (target, target),
        "    sget-object v2, %s->LOCAL:%s\n\n    :cond_2" % (target, target),
        "MFi missing-preference default (restore original LOCAL test-provider path)",
    )
    replace_once(
        host,
        "    sget-object v5, %s->LOCAL:%s\n\n    iput-object v5, p0, Lcom/shilapi/xcertplay/CarPlayHostActivity;->mfiTarget:%s" % (target, target, target),
        "    sget-object v5, %s->LOCAL:%s\n\n    iput-object v5, p0, Lcom/shilapi/xcertplay/CarPlayHostActivity;->mfiTarget:%s" % (target, target, target),
        "CarPlay host initial MFi target",
    )
    replace_once(
        choices,
        "    sget-object v2, %s->LOCAL:%s\n\n    sget v4, Lcom/shilapi/xcertplay/host/R$string;->local_offline:I" % (target, target),
        "    sget-object v2, %s->LOCAL:%s\n\n    sget v4, Lcom/shilapi/xcertplay/host/R$string;->local_offline:I" % (target, target),
        "MFi settings choices (restore original LOCAL test-provider choice)",
    )

    # Keep the original LOCAL provider code path and backend dispatch intact,
    # but never propagate the original bundled private identity. A LOCAL test
    # provider must be provisioned separately with an authorized test identity.
    # Hardware backends remain selectable; no automatic fallback is introduced.
    identity_dir = os.path.join(root, "assets/offline-mfi")
    if os.path.isdir(identity_dir):
        shutil.rmtree(identity_dir)
    if os.path.exists(identity_dir):
        raise SystemExit("offline MFi identity directory could not be removed")


    # AirPlay type-130 receive path: parsePackages() supplies a fresh owned
    # byte[] for each IAP body. Transfer that array directly into the private
    # pending queue instead of copying it a second time. Also close the tunnel
    # after releasing the queue monitor on overflow, so socket shutdown cannot
    # hold up recv()/offer() waiters.
    tunnel_stream = os.path.join(
        root, "smali_classes3/com/shilapi/xcertplay/airplay/AirPlayIapTunnelStream.smali"
    )
    replace_once(
        tunnel_stream,
        """    iget-object v2, p0, Lcom/shilapi/xcertplay/airplay/AirPlayIapTunnelStream;->tunnel:Lcom/shilapi/xcertplay/airplay/IapTunnel;

    invoke-virtual {v2}, Lcom/shilapi/xcertplay/airplay/IapTunnel;->close()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 114
    nop

    .end local v1    # "$i$a$-synchronized-AirPlayIapTunnelStream$offer$1":I
    monitor-exit v0

    return-void
""",
        """    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 114
    nop

    .end local v1    # "$i$a$-synchronized-AirPlayIapTunnelStream$offer$1":I
    monitor-exit v0

    iget-object v0, p0, Lcom/shilapi/xcertplay/airplay/AirPlayIapTunnelStream;->tunnel:Lcom/shilapi/xcertplay/airplay/IapTunnel;

    invoke-virtual {v0}, Lcom/shilapi/xcertplay/airplay/IapTunnel;->close()V

    return-void
""",
        "type-130 overflow closes tunnel outside queue monitor",
    )
    replace_once(
        tunnel_stream,
        """    iget-object v2, p0, Lcom/shilapi/xcertplay/airplay/AirPlayIapTunnelStream;->pending:Ljava/util/ArrayDeque;

    array-length v3, p1

    invoke-static {p1, v3}, Ljava/util/Arrays;->copyOf([BI)[B

    move-result-object v3

    const-string v4, "copyOf(...)"

    invoke-static {v3, v4}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {v2, v3}, Ljava/util/ArrayDeque;->addLast(Ljava/lang/Object;)V
""",
        """    iget-object v2, p0, Lcom/shilapi/xcertplay/airplay/AirPlayIapTunnelStream;->pending:Ljava/util/ArrayDeque;

    # parsePackages() already created an owned copyOfRange() body; transfer
    # ownership to this private queue rather than copying the same bytes again.
    invoke-virtual {v2, p1}, Ljava/util/ArrayDeque;->addLast(Ljava/lang/Object;)V
""",
        "type-130 remove redundant IAP body copy",
    )

    # Replace the repeated plaintext ArraysKt.plus()/parsePackages() copy chain
    # with an amortized-growth accumulator. Ciphertext framing/decryption stays
    # byte-for-byte on the original path.
    tunnel = os.path.join(root, "smali_classes3/com/shilapi/xcertplay/airplay/IapTunnel.smali")
    replace_once_any(
        tunnel,
        [
            (
                """    new-array v0, v0, [B\n\n    .line 131\n    .local v0, "plaintext":[B\n""",
                """    new-instance v0, Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;\n\n    iget-object v2, p0, Lcom/shilapi/xcertplay/airplay/IapTunnel;->listener:Lcom/shilapi/xcertplay/airplay/IapTunnel$Listener;\n\n    invoke-direct {v0, v2}, Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;-><init>(Ljava/lang/Object;)V\n\n    .line 131\n    .local v0, "packageAccumulator":Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;\n""",
            ),
            (
                """    .local v0, "plaintext":[B\n""",
                """    new-instance v0, Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;\n\n    iget-object v2, p0, Lcom/shilapi/xcertplay/airplay/IapTunnel;->listener:Lcom/shilapi/xcertplay/airplay/IapTunnel$Listener;\n\n    invoke-direct {v0, v2}, Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;-><init>(Ljava/lang/Object;)V\n\n    .local v0, "packageAccumulator":Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;\n""",
            ),
        ],
        "type-130 incremental package accumulator initialization",
    )
    replace_once(
        tunnel,
        """    invoke-static {v0, v9}, Lkotlin/collections/ArraysKt;->plus([B[B)[B\n\n    move-result-object v9\n\n    move-object v0, v9\n\n    .line 149\n""",
        """    invoke-virtual {v0, v9}, Lcom/shilapi/xcertplay/airplay/TunnelPackageAccumulator;->append([B)V\n\n    .line 149\n""",
        "type-130 remove repeated plaintext concatenation",
    )
    replace_once(
        tunnel,
        """    invoke-direct {p0, v0}, Lcom/shilapi/xcertplay/airplay/IapTunnel;->parsePackages([B)[B\n\n    move-result-object v9\n""",
        """    # Complete packages are dispatched by TunnelPackageAccumulator.append().\n""",
        "type-130 remove per-read parsePackages tail copy",
    )
    # The original parsePackages result was assigned back into v0. Once the
    # call is removed, that stale move would overwrite the accumulator with
    # the ciphertext array held in v9 on every read; remove it explicitly.
    replace_once(
        tunnel,
        """    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    move-object v0, v9

    .end local v7    # "read":I
""",
        """    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .end local v7    # "read":I
""",
        "type-130 retain accumulator register across reads",
    )


    # Replace decryptFrames' collection/concatenation pipeline with an exact-size
    # two-pass output. Frame authentication and counter order remain unchanged.
    with open(tunnel, "r", encoding="utf-8") as source:
        tunnel_text = source.read()
    method_start = ".method private final decryptFrames([B)Lkotlin/Pair;\n"
    start = tunnel_text.find(method_start)
    if start < 0 or tunnel_text.find(method_start, start + 1) >= 0:
        raise SystemExit("decryptFrames: expected exactly one method")
    method_end = tunnel_text.find("\n.end method", start)
    if method_end < 0:
        raise SystemExit("decryptFrames: method terminator not found")
    method_end += len("\n.end method")
    old_method = tunnel_text[start:method_end]
    new_method = r""".method private final decryptFrames([B)Lkotlin/Pair;
    .locals 13
    .param p1, "buffer"    # [B

    const/4 v0, 0x0
    const/4 v1, 0x0

    :scan_frames
    array-length v2, p1
    sub-int/2addr v2, v1
    const/4 v3, 0x2
    if-lt v2, v3, :scan_done
    invoke-direct {p0, p1, v1}, Lcom/shilapi/xcertplay/airplay/IapTunnel;->readU16Le([BI)I
    move-result v2
    add-int/lit8 v3, v2, 0x12
    array-length v4, p1
    sub-int/2addr v4, v1
    if-lt v4, v3, :scan_done
    add-int/2addr v0, v2
    add-int/2addr v1, v3
    goto :scan_frames

    :scan_done
    new-array v0, v0, [B
    const/4 v1, 0x0
    const/4 v2, 0x0

    :decrypt_frames
    array-length v3, p1
    sub-int/2addr v3, v1
    const/4 v4, 0x2
    if-lt v3, v4, :decrypt_done
    invoke-direct {p0, p1, v1}, Lcom/shilapi/xcertplay/airplay/IapTunnel;->readU16Le([BI)I
    move-result v3
    add-int/lit8 v4, v3, 0x12
    array-length v5, p1
    sub-int/2addr v5, v1
    if-lt v5, v4, :decrypt_done

    add-int/lit8 v5, v1, 0x2
    invoke-static {p1, v1, v5}, Lkotlin/collections/ArraysKt;->copyOfRange([BII)[B
    move-result-object v5
    add-int/lit8 v6, v1, 0x2
    add-int v7, v1, v4
    invoke-static {p1, v6, v7}, Lkotlin/collections/ArraysKt;->copyOfRange([BII)[B
    move-result-object v6

    sget-object v7, Lcom/shilapi/xcertplay/airplay/AirPlayCrypto;->INSTANCE:Lcom/shilapi/xcertplay/airplay/AirPlayCrypto;
    iget-object v8, p0, Lcom/shilapi/xcertplay/airplay/IapTunnel;->readKey:[B
    iget-object v9, p0, Lcom/shilapi/xcertplay/airplay/IapTunnel;->readCounter:Ljava/util/concurrent/atomic/AtomicLong;
    invoke-virtual {v9}, Ljava/util/concurrent/atomic/AtomicLong;->get()J
    move-result-wide v11
    invoke-virtual {v7, v11, v12}, Lcom/shilapi/xcertplay/airplay/AirPlayCrypto;->nonce64(J)[B
    move-result-object v9
    invoke-virtual {v7, v8, v9, v6, v5}, Lcom/shilapi/xcertplay/airplay/AirPlayCrypto;->chachaOpen([B[B[B[B)[B
    move-result-object v10

    iget-object v7, p0, Lcom/shilapi/xcertplay/airplay/IapTunnel;->readCounter:Ljava/util/concurrent/atomic/AtomicLong;
    invoke-virtual {v7}, Ljava/util/concurrent/atomic/AtomicLong;->incrementAndGet()J

    array-length v11, v10
    const/4 v12, 0x0
    invoke-static {v10, v12, v0, v2, v11}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V
    add-int/2addr v2, v11
    add-int/2addr v1, v4
    goto :decrypt_frames

    :decrypt_done
    array-length v3, p1
    invoke-static {p1, v1, v3}, Lkotlin/collections/ArraysKt;->copyOfRange([BII)[B
    move-result-object v3
    invoke-static {v0, v3}, Lkotlin/TuplesKt;->to(Ljava/lang/Object;Ljava/lang/Object;)Lkotlin/Pair;
    move-result-object v0
    return-object v0
.end method"""
    replace_once(tunnel, old_method, new_method, "type-130 exact-size decrypted frame output")

    print("Original DiPlay LOCAL MFi provider path restored (credential assets excluded); type-130 receive path uses incremental package accumulation.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
