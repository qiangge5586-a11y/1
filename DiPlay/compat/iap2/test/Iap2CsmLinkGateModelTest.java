package com.shilapi.xcertplay.compat.iap2;

public final class Iap2CsmLinkGateModelTest {
    private static int checks;

    public static void main(String[] args) {
        linkReadinessIsRequired();
        peerLimitIsRequiredAfterLinkReadiness();
        validPeerLimitCreatesChunkAdmission();
        invalidPeerLimitsAreRejected();
        closedLinkNeverCreatesAdmission();
        System.out.println("Iap2CsmLinkGateModelTest: PASS (" + checks + " checks)");
    }

    private static void linkReadinessIsRequired() {
        Iap2LinkEngineModel link = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        link.start(true, 0);
        Iap2CsmLinkGateModel.Result result = Iap2CsmLinkGateModel.prepare(
                link, Integer.valueOf(256), new byte[100], 0, 5000);
        eq(Iap2CsmLinkGateModel.Status.WAITING_FOR_LINK, result.status);
        truth(result.admission == null);
    }

    private static void peerLimitIsRequiredAfterLinkReadiness() {
        Iap2LinkEngineModel link = readyLink();
        Iap2CsmLinkGateModel.Result result = Iap2CsmLinkGateModel.prepare(
                link, null, new byte[100], 0, 5000);
        eq(Iap2CsmLinkGateModel.Status.WAITING_FOR_PEER_LIMIT, result.status);
        truth(result.admission == null);
    }

    private static void validPeerLimitCreatesChunkAdmission() {
        Iap2LinkEngineModel link = readyLink();
        Iap2CsmLinkGateModel.Result result = Iap2CsmLinkGateModel.prepare(
                link, Integer.valueOf(32), new byte[65], 100, 5000);
        eq(Iap2CsmLinkGateModel.Status.READY, result.status);
        truth(result.admission != null);
        eq(3, result.admission.totalChunkCount());
        eq(Iap2CsmSendAdmissionModel.Status.QUEUED,
                result.admission.step(100, new Iap2LinkCommandQueueModel()).status);
    }

    private static void invalidPeerLimitsAreRejected() {
        Iap2LinkEngineModel link = readyLink();
        eq(Iap2CsmLinkGateModel.Status.INVALID_PEER_LIMIT,
                Iap2CsmLinkGateModel.prepare(link, Integer.valueOf(0),
                        new byte[1], 0, 1000).status);
        eq(Iap2CsmLinkGateModel.Status.INVALID_PEER_LIMIT,
                Iap2CsmLinkGateModel.prepare(link, Integer.valueOf(65526),
                        new byte[1], 0, 1000).status);
    }

    private static void closedLinkNeverCreatesAdmission() {
        Iap2LinkEngineModel link = readyLink();
        link.endOfStream();
        Iap2CsmLinkGateModel.Result result = Iap2CsmLinkGateModel.prepare(
                link, Integer.valueOf(64), new byte[1], 0, 1000);
        eq(Iap2CsmLinkGateModel.Status.CLOSED, result.status);
        truth(result.admission == null);
    }

    private static Iap2LinkEngineModel readyLink() {
        Iap2LinkEngineModel link = new Iap2LinkEngineModel(new Iap2LinkNegotiationModel());
        link.start(true, 0);
        link.takeNegotiationOutput();
        byte[] sync = Iap2LinkFrameCodec.encodeSynchronizationPayload(
                4, 4096, 1500, 250, 3, 5,
                new int[][] {{10, 0, 2}, {11, 2, 1}});
        link.feedNegotiation(Iap2LinkFrameCodec.encode(0x80, 77, 0, 0, sync), 1);
        link.takeNegotiationOutput();
        link.feedNegotiation(Iap2LinkFrameCodec.encode(0x40, 77, 99, 0, null), 2);
        eq(Iap2LinkEngineModel.State.READY, link.state());
        return link;
    }

    private static void eq(int a, int b) {
        checks++;
        if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void eq(Object a, Object b) {
        checks++;
        if (a != b) throw new AssertionError(a + " != " + b);
    }
    private static void truth(boolean value) {
        checks++;
        if (!value) throw new AssertionError("expected true");
    }
}
