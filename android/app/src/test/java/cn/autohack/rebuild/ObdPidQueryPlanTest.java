package cn.autohack.rebuild;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public final class ObdPidQueryPlanTest {
    private String bitmap(int basePid, int... supportedPids) {
        long mask = 0L;
        for (int pid : supportedPids) {
            int offset = pid - basePid;
            if (offset < 1 || offset > 32) throw new IllegalArgumentException("PID outside bitmap");
            mask |= 1L << (32 - offset);
        }
        return String.format(Locale.ROOT, "41%02X%02X%02X%02X%02X", basePid,
                (int) ((mask >>> 24) & 0xFF), (int) ((mask >>> 16) & 0xFF),
                (int) ((mask >>> 8) & 0xFF), (int) (mask & 0xFF));
    }

    @Test public void startsWithStandardSupportedPidQuery() {
        assertEquals("0100", ObdPidQueryPlan.initialBitmapCommand());
    }

    @Test public void followsOnlyAdvertisedBitmapExtensions() {
        assertEquals("0120", ObdPidQueryPlan.nextBitmapCommand(bitmap(0x00, 0x20), 0x00));
        assertEquals("0140", ObdPidQueryPlan.nextBitmapCommand(bitmap(0x20, 0x40), 0x20));
        assertNull(ObdPidQueryPlan.nextBitmapCommand(bitmap(0x00, 0x0C), 0x00));
        assertNull(ObdPidQueryPlan.nextBitmapCommand(bitmap(0x40, 0x5C), 0x40));
    }

    @Test public void schedulesOnlySupportedDashboardReadPidsInStableOrder() {
        List<String> commands = ObdPidQueryPlan.supportedReadCommands(
                bitmap(0x00, 0x0C, 0x0D),
                bitmap(0x20, 0x2F, 0x33),
                bitmap(0x40, 0x5C, 0x5E));
        assertEquals(Arrays.asList("010C", "010D", "012F", "0133", "015C", "015E"), commands);
    }

    @Test public void absentOrMalformedBitmapsNeverInventSupport() {
        assertEquals(Collections.emptyList(),
                ObdPidQueryPlan.supportedReadCommands(null, "NO DATA", null));
        assertNull(ObdPidQueryPlan.nextBitmapCommand("NO DATA", 0x00));
        assertNull(ObdPidQueryPlan.nextBitmapCommand(bitmap(0x00, 0x20), 0x40));
    }

    @Test public void rejectsPidsOutsideTheSelectedBitmapRange() {
        assertFalse(ObdPidQueryPlan.isPidSupported(bitmap(0x00, 0x0C), 0x00, 0x21));
        assertFalse(ObdPidQueryPlan.isPidSupported(bitmap(0x20, 0x2F), 0x20, 0x0C));
    }
}
