package cn.autohack.rebuild;

import java.util.Locale;

/** Decodes the standard Mode 01 PID 01 MIL state and stored DTC count. */
public final class ObdReadinessDecoder {
    private ObdReadinessDecoder() {}

    public static Status decode(String response) {
        if (response == null) return null;
        String compact = response.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        int marker = compact.indexOf("4101");
        if (marker < 0) return null;
        String payload = compact.substring(marker + 4);
        if (payload.length() < 8) return null;
        try {
            int status = Integer.parseInt(payload.substring(0, 2), 16);
            int support = Integer.parseInt(payload.substring(2, 4), 16);
            int incomplete = Integer.parseInt(payload.substring(4, 6), 16);
            int additional = Integer.parseInt(payload.substring(6, 8), 16);
            return new Status((status & 0x80) != 0, status & 0x7F,
                    support, incomplete, additional);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    public static final class Status {
        public final boolean milOn;
        public final int storedDtcCount;
        public final int monitorSupportByte;
        public final int monitorIncompleteByte;
        public final int additionalMonitorByte;

        Status(boolean milOn, int storedDtcCount, int monitorSupportByte,
                int monitorIncompleteByte, int additionalMonitorByte) {
            this.milOn = milOn;
            this.storedDtcCount = storedDtcCount;
            this.monitorSupportByte = monitorSupportByte;
            this.monitorIncompleteByte = monitorIncompleteByte;
            this.additionalMonitorByte = additionalMonitorByte;
        }
    }
}
