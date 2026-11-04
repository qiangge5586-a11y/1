package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ElmProtocolInfoTest {
    @Test public void combinesDescriptionAndAutomaticProtocolNumber() {
        assertEquals("ISO 15765-4 (CAN 11/500) (A6)",
                ElmProtocolInfo.describe("ISO 15765-4 (CAN 11/500)\r>", "A6>"));
    }

    @Test public void handlesMissingOrUnknownResponses() {
        assertEquals("protocol not reported", ElmProtocolInfo.describe(null, "?"));
        assertEquals("protocol 6", ElmProtocolInfo.describe("", "6"));
        assertEquals("ISO 9141-2", ElmProtocolInfo.describe(" ISO 9141-2 ", null));
    }

    @Test public void stripsLineBreaksAndBoundsAdapterText() {
        assertEquals("CAN 11/500 (A6)",
                ElmProtocolInfo.describe("CAN\n11/500", "A6\r"));
        assertTrue(ElmProtocolInfo.describe(repeat("X", 100), "").length() <= 80);
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
