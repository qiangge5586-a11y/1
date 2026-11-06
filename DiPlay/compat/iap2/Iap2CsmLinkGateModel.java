package com.shilapi.xcertplay.compat.iap2;

/**
 * Offline composition gate for the source-audited CSM send preconditions.
 * It creates a chunk-admission model only after the link is READY and the
 * peer's CSM control-payload limit has been learned. No transport I/O occurs.
 */
public final class Iap2CsmLinkGateModel {
    public enum Status {
        WAITING_FOR_LINK, WAITING_FOR_PEER_LIMIT, READY, CLOSED, INVALID_PEER_LIMIT
    }

    public static final class Result {
        public final Status status;
        public final Iap2CsmSendAdmissionModel admission;
        public final String reason;

        private Result(Status status, Iap2CsmSendAdmissionModel admission, String reason) {
            this.status = status;
            this.admission = admission;
            this.reason = reason;
        }
    }

    private Iap2CsmLinkGateModel() { }

    /**
     * Evaluates send preconditions without starting admission while either
     * the link state or peer CSM chunk limit is unavailable.
     */
    public static Result prepare(Iap2LinkEngineModel link, Integer peerMaxControlPayloadBytes,
            byte[] encodedFrame, long startedAtNanos, long timeoutMillis) {
        if (link == null) throw new NullPointerException("link");
        if (encodedFrame == null) throw new NullPointerException("encodedFrame");
        Iap2LinkEngineModel.State linkState = link.state();
        if (linkState == Iap2LinkEngineModel.State.CLOSED) {
            return new Result(Status.CLOSED, null, link.getTerminalReason());
        }
        if (linkState != Iap2LinkEngineModel.State.READY) {
            return new Result(Status.WAITING_FOR_LINK, null, null);
        }
        if (peerMaxControlPayloadBytes == null) {
            return new Result(Status.WAITING_FOR_PEER_LIMIT, null, null);
        }
        if (peerMaxControlPayloadBytes.intValue() < 1
                || peerMaxControlPayloadBytes.intValue() > Iap2CsmSendPlan.MAX_LINK_CHUNK_BYTES) {
            return new Result(Status.INVALID_PEER_LIMIT, null,
                    "peer CSM control-payload limit outside 1..65525");
        }
        Iap2CsmSendAdmissionModel admission = new Iap2CsmSendAdmissionModel(
                encodedFrame, peerMaxControlPayloadBytes.intValue(),
                startedAtNanos, timeoutMillis);
        return new Result(Status.READY, admission, null);
    }
}
