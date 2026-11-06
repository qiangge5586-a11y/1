package com.shilapi.xcertplay.compat.mfi;

import java.util.Locale;

/**
 * Fail-closed backend selection policy for the compatibility implementation.
 * LOCAL is permitted only when an explicit test-mode flag is supplied.
 * A backend failure is terminal for that attempt; callers must not silently switch.
 */
public final class MfiBackendSelectionPolicy {
    public enum Backend { LOCAL, USB_CH341, I2C, REMOTE }

    public static final class SelectionException extends IllegalArgumentException {
        public SelectionException(String message) { super(message); }
    }

    private MfiBackendSelectionPolicy() { }

    public static Backend resolve(String persistedValue, boolean explicitTestMode) {
        if (persistedValue == null || persistedValue.trim().length() == 0)
            throw new SelectionException("MFi backend selection is missing");
        final Backend backend;
        try {
            backend = Backend.valueOf(persistedValue.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException invalid) {
            throw new SelectionException("MFi backend selection is unsupported");
        }
        if (backend == Backend.LOCAL && !explicitTestMode)
            throw new SelectionException("LOCAL MFi backend requires explicit test mode");
        return backend;
    }

    /** A failed selected backend must be surfaced; there is no implicit fallback. */
    public static boolean mayAutomaticallyFallback(Backend selected) {
        if (selected == null) throw new IllegalArgumentException("selected == null");
        return false;
    }
}
