package com.shilapi.xcertplay.compat.iap2;

public final class Iap2CsmReceiveQueueModelTest {
    private static int checks;

    public static void main(String[] args) {
        enforcesFrameCountAndFifoAccounting();
        enforcesEncodedByteLimit();
        clonesPayloadAndRejectsAfterClose();
        System.out.println("Iap2CsmReceiveQueueModelTest: PASS (" + checks + " checks)");
    }

    private static void enforcesFrameCountAndFifoAccounting() {
        Iap2CsmReceiveQueueModel q = new Iap2CsmReceiveQueueModel();
        for (int i = 0; i < 64; i++) truth(q.enqueue(i, new byte[] {(byte) i}));
        eq(64, q.size());
        eq(64 * 7, q.pendingBytes());
        falsity(q.enqueue(64, new byte[0]));
        truth(q.isClosed());
        eq("CSM received-frame queue limit exceeded", q.getTerminalReason());
        eq(64, q.size());
        for (int i = 0; i < 64; i++) {
            Iap2CsmReceiveQueueModel.Frame frame = q.poll();
            truth(frame != null);
            eq(i, frame.messageId);
        }
        eq(0, q.size());
        eq(0, q.pendingBytes());
    }

    private static void enforcesEncodedByteLimit() {
        Iap2CsmReceiveQueueModel q = new Iap2CsmReceiveQueueModel();
        byte[] maximumPayload = new byte[65535 - 6];
        for (int i = 0; i < 16; i++) truth(q.enqueue(i, maximumPayload));
        eq(16 * 65535, q.pendingBytes());
        // 16-byte encoded frame exactly fills the remaining 16 bytes.
        truth(q.enqueue(16, new byte[10]));
        eq(1024 * 1024, q.pendingBytes());
        falsity(q.enqueue(17, new byte[0]));
        truth(q.isClosed());
        eq(17, q.size());
        for (int i = 0; i < 17; i++) truth(q.poll() != null);
        eq(0, q.pendingBytes());
    }

    private static void clonesPayloadAndRejectsAfterClose() {
        Iap2CsmReceiveQueueModel q = new Iap2CsmReceiveQueueModel();
        byte[] payload = new byte[] {1, 2, 3};
        truth(q.enqueue(9, payload));
        payload[0] = 99;
        Iap2CsmReceiveQueueModel.Frame frame = q.poll();
        eq(1, frame.payload[0] & 255);
        for (int i = 0; i < 64; i++) truth(q.enqueue(i, new byte[0]));
        falsity(q.enqueue(65, new byte[0]));
        falsity(q.enqueue(66, new byte[0]));
    }

    private static void truth(boolean v) { checks++; if (!v) throw new AssertionError("expected true"); }
    private static void falsity(boolean v) { checks++; if (v) throw new AssertionError("expected false"); }
    private static void eq(int a, int b) { checks++; if (a != b) throw new AssertionError(a + " != " + b); }
    private static void eq(String a, String b) { checks++; if (a == null ? b != null : !a.equals(b)) throw new AssertionError(a + " != " + b); }
}
