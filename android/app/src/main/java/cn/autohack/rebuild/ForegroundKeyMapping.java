package cn.autohack.rebuild;

import java.util.Arrays;

/** Pure key-code binding helpers for foreground-only in-app navigation actions. */
public final class ForegroundKeyMapping {
    public static final int SLOT_COUNT = 10;
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_POWER = 26;

    private ForegroundKeyMapping() {}

    public static boolean isCapturable(int keyCode) {
        return keyCode != KEYCODE_UNKNOWN && keyCode != KEYCODE_HOME
                && keyCode != KEYCODE_BACK && keyCode != KEYCODE_VOLUME_UP
                && keyCode != KEYCODE_VOLUME_DOWN && keyCode != KEYCODE_POWER;
    }

    /** Assigns a key to one slot and clears any duplicate assignment from other slots. */
    public static int[] assign(int[] current, int slot, int keyCode) {
        int[] result = current == null ? new int[SLOT_COUNT] : Arrays.copyOf(current, SLOT_COUNT);
        if (slot < 0 || slot >= SLOT_COUNT || !isCapturable(keyCode)) return result;
        for (int i = 0; i < result.length; i++) {
            if (i != slot && result[i] == keyCode) result[i] = KEYCODE_UNKNOWN;
        }
        result[slot] = keyCode;
        return result;
    }

    public static int findSlot(int keyCode, int[] bindings) {
        if (keyCode == KEYCODE_UNKNOWN || bindings == null) return -1;
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] == keyCode) return i;
        }
        return -1;
    }
}
