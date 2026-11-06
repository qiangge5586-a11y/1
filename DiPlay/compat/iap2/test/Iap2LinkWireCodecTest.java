package com.shilapi.xcertplay.compat.iap2.test;

import com.shilapi.xcertplay.compat.iap2.Iap2LinkWireCodec;
import java.util.Arrays;

public final class Iap2LinkWireCodecTest {
    private static int checks;

    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }

    private static byte[] hex(String value) {
        String[] parts = value.split(" ");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }

    public static void main(String[] args) {
        byte[] sync = Iap2LinkWireCodec.encodeSynchronizationPayload(
                4, 65535, 4000, 100, 4, 3,
                new int[][] {{10, 0, 2}, {11, 2, 1}, {12, 1, 2}});
        check(Arrays.equals(sync, hex("01 04 ff ff 0f a0 00 64 04 03 0a 00 02 0b 02 01 0c 01 02")),
                "sync payload golden vector");
        int[] decoded = Iap2LinkWireCodec.decodeSynchronizationPayload(sync);
        check(decoded != null && decoded.length == 15, "sync decode field count");
        check(decoded[0] == 4 && decoded[1] == 65535 && decoded[2] == 4000
                && decoded[3] == 100 && decoded[4] == 4 && decoded[5] == 3,
                "sync decode fixed fields");
        check(decoded[6] == 10 && decoded[7] == 0 && decoded[8] == 2
                && decoded[9] == 11 && decoded[10] == 2 && decoded[11] == 1
                && decoded[12] == 12 && decoded[13] == 1 && decoded[14] == 2,
                "sync decode session descriptors");
        check(Iap2LinkWireCodec.decodeSynchronizationPayload(new byte[9]) == null,
                "short sync payload rejected");
        check(Iap2LinkWireCodec.decodeSynchronizationPayload(new byte[] {2,0,0,0,0,0,0,0,0,0}) == null,
                "unknown sync version rejected");

        byte[] syncFrame = Iap2LinkWireCodec.encodeFrame(sync, 99, 0x80, 0, 0);
        check(Arrays.equals(syncFrame, hex("ff 5a 00 1d 80 63 00 00 a7 01 04 ff ff 0f a0 00 64 04 03 0a 00 02 0b 02 01 0c 01 02 ba")),
                "synchronization link frame golden vector");
        check(syncFrame.length == 29 && Iap2LinkWireCodec.checksumValid(Arrays.copyOf(syncFrame, 9)),
                "sync frame header checksum");
        check(Iap2LinkWireCodec.checksumValid(Arrays.copyOfRange(syncFrame, 9, syncFrame.length)),
                "sync frame payload checksum");

        byte[] ack = Iap2LinkWireCodec.encodeFrame(null, 99, 0x40, 99, 0);
        check(Arrays.equals(ack, hex("ff 5a 00 09 40 63 63 00 98")), "ACK frame golden vector");
        byte[] data = Iap2LinkWireCodec.encodeFrame(new byte[] {0x12, 0x34}, 100, 0x40, 99, 10);
        check(Arrays.equals(data, hex("ff 5a 00 0c 40 64 63 0a 8a 12 34 ba")),
                "data frame golden vector");

        Iap2LinkWireCodec.Header parsed = Iap2LinkWireCodec.parseHeader(data.length >= 9
                ? Arrays.copyOf(data, 9) : data);
        check(parsed != null && parsed.length == 12 && parsed.control == 0x40
                && parsed.sequence == 100 && parsed.acknowledgement == 99
                && parsed.sessionId == 10, "header decode");
        byte[] badHeader = Arrays.copyOf(data, 9);
        badHeader[8] ^= 1;
        check(Iap2LinkWireCodec.parseHeader(badHeader) == null, "bad header checksum rejected");
        badHeader = Arrays.copyOf(data, 9);
        badHeader[1] = 0x55;
        check(Iap2LinkWireCodec.parseHeader(badHeader) == null, "bad header marker rejected");

        check(Iap2LinkWireCodec.nextSequence(99) == 100, "sequence increments");
        check(Iap2LinkWireCodec.nextSequence(255) == 0, "sequence wraps");
        check(Iap2LinkWireCodec.sequenceDistance(2, 254) == 4, "sequence distance wraps");
        check(Iap2LinkWireCodec.sequenceDistance(100, 99) == 1, "sequence distance ordinary");
        check(Arrays.equals(Iap2LinkWireCodec.MARKER, hex("ff 55 02 00 ee 10")),
                "initial marker golden vector");

        boolean rejected = false;
        try {
            Iap2LinkWireCodec.encodeFrame(new byte[65526], 0, 0, 0, 0);
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "oversized payload rejected");
        System.out.println("Iap2LinkWireCodecTest: PASS (" + checks + " checks)");
    }
}
