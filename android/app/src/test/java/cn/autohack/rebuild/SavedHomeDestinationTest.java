package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SavedHomeDestinationTest {
    @Test public void trimsAndNormalizesWhitespace() {
        assertEquals("Kuala Lumpur Airport",
                SavedHomeDestination.normalize("  Kuala   Lumpur Airport  "));
    }

    @Test public void rejectsEmptyAndOverlongDestinations() {
        assertFalse(SavedHomeDestination.isValid("   "));
        assertFalse(SavedHomeDestination.isValid(repeat('x', 161)));
    }

    @Test public void rejectsEmbeddedNullAndOtherControlCharacters() {
        assertFalse(SavedHomeDestination.isValid("Home\\u0000Address"));
        assertFalse(SavedHomeDestination.isValid("Home\\u0007Address"));
    }

    @Test public void allowsAndNormalizesOrdinaryWhitespace() {
        assertEquals("Home Address",
                SavedHomeDestination.normalize("Home\\n  Address"));
    }

    @Test public void acceptsMaximumLengthDestination() {
        assertTrue(SavedHomeDestination.isValid(repeat('x', 160)));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
