package com.shilapi.xcertplay.compat.iap2;

import java.util.Arrays;
import java.util.List;

public final class Iap2CsmSendPlanTest {
    private static int checks;

    public static void main(String[] args) {
        splitsAtPeerAdvertisedLimit();
        handlesExactAndPartialFinalChunks();
        supportsOneBytePeerLimitAndMaximumLimit();
        validatesBoundsAndDefensiveCopies();
        emptyFrameProducesNoChunks();
        System.out.println("Iap2CsmSendPlanTest: PASS (" + checks + " checks)");
    }

    private static void splitsAtPeerAdvertisedLimit() {
        byte[] frame = sequence(1000);
        List<byte[]> chunks = Iap2CsmSendPlan.splitForLink(frame, 127);
        eq(8, chunks.size());
        int offset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            byte[] chunk = chunks.get(i);
            truth(chunk.length <= 127);
            bytes(Arrays.copyOfRange(frame, offset, offset + chunk.length), chunk);
            offset += chunk.length;
        }
        eq(frame.length, offset);
        eq(8, Iap2CsmSendPlan.chunkCount(frame.length, 127));
    }

    private static void handlesExactAndPartialFinalChunks() {
        eq(1, Iap2CsmSendPlan.chunkCount(65525, 65525));
        eq(2, Iap2CsmSendPlan.chunkCount(65526, 65525));
        eq(3, Iap2CsmSendPlan.chunkCount(65525, 32763));
        List<byte[]> exact = Iap2CsmSendPlan.splitForLink(sequence(65525), 65525);
        eq(1, exact.size());
        eq(65525, exact.get(0).length);
        List<byte[]> partial = Iap2CsmSendPlan.splitForLink(sequence(65526), 65525);
        eq(2, partial.size());
        eq(65525, partial.get(0).length);
        eq(1, partial.get(1).length);
    }

    private static void supportsOneBytePeerLimitAndMaximumLimit() {
        List<byte[]> one = Iap2CsmSendPlan.splitForLink(sequence(257), 1);
        eq(257, one.size());
        for (int i = 0; i < one.size(); i++) eq(1, one.get(i).length);
        List<byte[]> max = Iap2CsmSendPlan.splitForLink(sequence(65535), 65525);
        eq(2, max.size());
        eq(65525, max.get(0).length);
        eq(10, max.get(1).length);
    }

    private static void validatesBoundsAndDefensiveCopies() {
        expectIllegalArgument(new Runnable() { public void run() {
            Iap2CsmSendPlan.splitForLink(new byte[1], 0);
        }});
        expectIllegalArgument(new Runnable() { public void run() {
            Iap2CsmSendPlan.splitForLink(new byte[1], 65526);
        }});
        expectIllegalArgument(new Runnable() { public void run() {
            Iap2CsmSendPlan.chunkCount(-1, 1);
        }});
        expectIllegalArgument(new Runnable() { public void run() {
            Iap2CsmSendPlan.chunkCount(1, 65526);
        }});
        byte[] input = new byte[] {1,2,3};
        List<byte[]> chunks = Iap2CsmSendPlan.splitForLink(input, 2);
        input[0] = 99;
        eq(1, chunks.get(0)[0] & 255);
        chunks.get(0)[0] = 88;
        eq(1, input[0] & 255);
        expectUnsupported(new Runnable() { public void run() {
            chunks.add(new byte[1]);
        }});
    }

    private static void emptyFrameProducesNoChunks() {
        eq(0, Iap2CsmSendPlan.splitForLink(new byte[0], 1).size());
        eq(0, Iap2CsmSendPlan.chunkCount(0, 1));
    }

    private static byte[] sequence(int length) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) out[i] = (byte) i;
        return out;
    }
    private static void bytes(byte[] a, byte[] b) { checks++; if (!Arrays.equals(a,b)) throw new AssertionError("byte mismatch"); }
    private static void eq(int a, int b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void truth(boolean b) { checks++; if (!b) throw new AssertionError("expected true"); }
    private static void expectIllegalArgument(Runnable r) {
        checks++; try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
    private static void expectUnsupported(Runnable r) {
        checks++; try { r.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("expected UnsupportedOperationException");
    }
}
