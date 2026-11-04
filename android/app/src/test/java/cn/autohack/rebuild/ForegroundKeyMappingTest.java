package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ForegroundKeyMappingTest {
    @Test public void assignsAndFindsTenIndependentSlots() {
        int[] keys = new int[ForegroundKeyMapping.SLOT_COUNT];
        keys = ForegroundKeyMapping.assign(keys, 0, 19);
        keys = ForegroundKeyMapping.assign(keys, 9, 22);
        assertEquals(0, ForegroundKeyMapping.findSlot(19, keys));
        assertEquals(9, ForegroundKeyMapping.findSlot(22, keys));
        assertEquals(-1, ForegroundKeyMapping.findSlot(20, keys));
    }

    @Test public void reassignmentClearsDuplicateKeyFromEarlierSlot() {
        int[] keys = new int[ForegroundKeyMapping.SLOT_COUNT];
        keys = ForegroundKeyMapping.assign(keys, 1, 21);
        keys = ForegroundKeyMapping.assign(keys, 4, 21);
        assertEquals(0, keys[1]);
        assertEquals(21, keys[4]);
        assertEquals(4, ForegroundKeyMapping.findSlot(21, keys));
    }

    @Test public void refusesSystemNavigationAndVolumeKeys() {
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_UNKNOWN));
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_HOME));
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_BACK));
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_VOLUME_UP));
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_VOLUME_DOWN));
        assertFalse(ForegroundKeyMapping.isCapturable(ForegroundKeyMapping.KEYCODE_POWER));
        assertTrue(ForegroundKeyMapping.isCapturable(19));
    }

    @Test public void invalidSlotOrKeyLeavesBindingsUnchanged() {
        int[] keys = new int[ForegroundKeyMapping.SLOT_COUNT];
        keys[2] = 20;
        assertArrayEquals(keys, ForegroundKeyMapping.assign(keys, -1, 21));
        assertArrayEquals(keys, ForegroundKeyMapping.assign(keys, 10, 21));
        assertArrayEquals(keys, ForegroundKeyMapping.assign(keys, 2,
                ForegroundKeyMapping.KEYCODE_BACK));
    }
}
