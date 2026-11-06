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
        "    sget-object v2, %s->I2C:%s\n\n    :cond_2" % (target, target),
        "MFi missing-preference default",
    )
    replace_once(
        host,
        "    sget-object v5, %s->LOCAL:%s\n\n    iput-object v5, p0, Lcom/shilapi/xcertplay/CarPlayHostActivity;->mfiTarget:%s" % (target, target, target),
        "    sget-object v5, %s->I2C:%s\n\n    iput-object v5, p0, Lcom/shilapi/xcertplay/CarPlayHostActivity;->mfiTarget:%s" % (target, target, target),
        "CarPlay host initial MFi target",
    )
    replace_once(
        choices,
        "    sget-object v2, %s->LOCAL:%s\n\n    sget v4, Lcom/shilapi/xcertplay/host/R$string;->local_offline:I" % (target, target),
        "    sget-object v2, %s->I2C:%s\n\n    sget v4, Lcom/shilapi/xcertplay/host/R$string;->i2c:I" % (target, target),
        "MFi settings choices (hardware-only)",
    )

    # The original APK carries a bundled offline MFi identity. Never propagate
    # that private identity into the merged test artifact; use hardware-backed
    # I2C by default and retain USB/CH341 as the alternate hardware option.
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

    print("Original DiPlay MFi defaults patched to I2C; offline identity excluded.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
