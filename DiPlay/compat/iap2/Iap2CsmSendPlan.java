package com.shilapi.xcertplay.compat.iap2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline mirror of Iap2CsmFramer.splitForLink().
 * Splits one already-encoded CSM frame into peer-sized link-control payloads.
 * It does not enqueue data or perform transport I/O.
 */
public final class Iap2CsmSendPlan {
    public static final int MAX_LINK_CHUNK_BYTES = 65525;

    private Iap2CsmSendPlan() { }

    public static List<byte[]> splitForLink(byte[] encodedFrame, int peerMaxControlPayloadBytes) {
        if (encodedFrame == null) throw new NullPointerException("encodedFrame");
        if (peerMaxControlPayloadBytes < 1
                || peerMaxControlPayloadBytes > MAX_LINK_CHUNK_BYTES) {
            throw new IllegalArgumentException("peer max control payload must be 1..65525");
        }
        ArrayList<byte[]> chunks = new ArrayList<byte[]>();
        int offset = 0;
        while (offset < encodedFrame.length) {
            int count = Math.min(peerMaxControlPayloadBytes, encodedFrame.length - offset);
            byte[] chunk = new byte[count];
            System.arraycopy(encodedFrame, offset, chunk, 0, count);
            chunks.add(chunk);
            offset += count;
        }
        return Collections.unmodifiableList(chunks);
    }

    public static int chunkCount(int encodedFrameBytes, int peerMaxControlPayloadBytes) {
        if (encodedFrameBytes < 0) throw new IllegalArgumentException("encodedFrameBytes");
        if (peerMaxControlPayloadBytes < 1
                || peerMaxControlPayloadBytes > MAX_LINK_CHUNK_BYTES) {
            throw new IllegalArgumentException("peer max control payload must be 1..65525");
        }
        if (encodedFrameBytes == 0) return 0;
        return 1 + (encodedFrameBytes - 1) / peerMaxControlPayloadBytes;
    }
}
