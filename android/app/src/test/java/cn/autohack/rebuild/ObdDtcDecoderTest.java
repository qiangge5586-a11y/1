package cn.autohack.rebuild;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class ObdDtcDecoderTest {
    @Test public void decodesPositiveStoredDtcResponse() {
        assertEquals(Arrays.asList("P0133", "P0123"),
                ObdDtcDecoder.decodeStoredCodes("03\r43 01 33 01 23>"));
    }

    @Test public void acceptsOptionalCanHeaderAndSkipsZeroPadding() {
        assertEquals(Collections.singletonList("P0133"),
                ObdDtcDecoder.decodeStoredCodes("7E8 04 43 01 33 00 00"));
    }

    @Test public void decodesAllFourDtcCategories() {
        assertEquals(Arrays.asList("P0133", "C0133", "B0133", "U0133"),
                ObdDtcDecoder.decodeStoredCodes("43 01 33 41 33 81 33 C1 33"));
    }

    @Test public void preservesOrderAndRemovesDuplicates() {
        assertEquals(Arrays.asList("P0133", "P0123"),
                ObdDtcDecoder.decodeStoredCodes("43 01 33 01 23 01 33"));
    }

    @Test public void decodesPendingAndPermanentModeResponses() {
        assertEquals(Arrays.asList("P0133"),
                ObdDtcDecoder.decodePendingCodes("07\\r47 01 33>"));
        assertEquals(Arrays.asList("P0420"),
                ObdDtcDecoder.decodePermanentCodes("0A\\r4A 04 20>"));
        assertTrue(ObdDtcDecoder.decodePendingCodes("43 01 33").isEmpty());
        assertTrue(ObdDtcDecoder.decodePermanentCodes("47 01 33").isEmpty());
    }

    @Test public void noDataNegativeAndNullResponsesReturnEmpty() {
        assertTrue(ObdDtcDecoder.decodeStoredCodes(null).isEmpty());
        assertTrue(ObdDtcDecoder.decodeStoredCodes("NO DATA").isEmpty());
        assertTrue(ObdDtcDecoder.decodeStoredCodes("7F 03 11").isEmpty());
        assertTrue(ObdDtcDecoder.decodeStoredCodes("43 00 00").isEmpty());
    }

    @Test public void truncatedAndOversizedResponsesAreRejected() {
        assertTrue(ObdDtcDecoder.decodeStoredCodes("43 01 3").isEmpty());
        StringBuilder huge = new StringBuilder("43 ");
        for (int i = 0; i < 70; i++) huge.append("01 33 ");
        assertTrue(ObdDtcDecoder.decodeStoredCodes(huge.toString()).isEmpty());
    }
}
