package com.shilapi.xcertplay.network.safety;

/**
 * Pure decision layer for startup recovery. It never starts/stops services or
 * restores files; it classifies persisted state so a caller can fail closed.
 */
public final class HotspotRecoveryPolicy {
    public enum Decision {
        NO_JOURNAL,
        NO_SYSTEM_CHANGE_RECORDED,
        VERIFY_ACTIVE_PROCESS,
        CLEAN_ROLLED_BACK,
        MANUAL_RECOVERY_REQUIRED
    }

    private HotspotRecoveryPolicy() {}

    public static Decision classify(AppendOnlyHotspotJournal.Entry entry) {
        if (entry == null) return Decision.NO_JOURNAL;
        if (entry.state == null) return Decision.MANUAL_RECOVERY_REQUIRED;
        switch (entry.state) {
            case NEW:
            case PREFLIGHTED:
            case BACKED_UP:
                return Decision.NO_SYSTEM_CHANGE_RECORDED;
            case ACTIVE:
                return Decision.VERIFY_ACTIVE_PROCESS;
            case ROLLED_BACK:
                return Decision.CLEAN_ROLLED_BACK;
            case APPLYING:
            case ROLLING_BACK:
            case RECOVERY_REQUIRED:
            default:
                return Decision.MANUAL_RECOVERY_REQUIRED;
        }
    }
}
