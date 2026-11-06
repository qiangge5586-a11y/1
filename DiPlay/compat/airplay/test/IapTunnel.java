package com.shilapi.xcertplay.airplay;

/** Test-only compile stub; the production listener is supplied by original DiPlay classes3 DEX. */
public final class IapTunnel {
    public interface Listener {
        void onDebug(String message);
        void onIap(byte[] bytes);
    }
}
