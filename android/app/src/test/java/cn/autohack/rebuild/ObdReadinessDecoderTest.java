package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ObdReadinessDecoderTest {
    @Test public void decodesMilOnAndStoredCodeCount() {
        ObdReadinessDecoder.Status status =
                ObdReadinessDecoder.decode("01\\r41 01 83 7F 00 00>");
        assertNotNull(status);
        assertTrue(status.milOn);
        assertEquals(3, status.storedDtcCount);
        assertEquals(0x7F, status.monitorSupportByte);
    }

    @Test public void decodesMilOffAndZeroStoredCodes() {
        ObdReadinessDecoder.Status status =
                ObdReadinessDecoder.decode("7E8 06 41 01 00 00 00 00");
        assertNotNull(status);
        assertFalse(status.milOn);
        assertEquals(0, status.storedDtcCount);
    }

    @Test public void rejectsNoDataNegativeAndTruncatedResponses() {
        assertNull(ObdReadinessDecoder.decode(null));
        assertNull(ObdReadinessDecoder.decode("NO DATA"));
        assertNull(ObdReadinessDecoder.decode("7F 01 11"));
        assertNull(ObdReadinessDecoder.decode("41 01 80 07"));
    }
}
