package com.shilapi.xcertplay.compat.iap2;

import java.io.IOException;

/**
 * API-17-compatible full-duplex byte stream used by the production iAP2 pump.
 *
 * read() contract: positive = bytes read, 0 = timeout/no data, -1 = orderly EOF.
 * write() contract: writes the entire requested range or throws IOException.
 * Implementations must make close() idempotent and unblock a blocked read when closed.
 *
 * This interface deliberately contains no Bluetooth, USB, TCP, or Android dependencies.
 */
public interface Iap2DuplexByteStream {
    int read(byte[] buffer, int offset, int length, int timeoutMillis) throws IOException;

    void write(byte[] buffer, int offset, int length) throws IOException;

    void close() throws IOException;
}
