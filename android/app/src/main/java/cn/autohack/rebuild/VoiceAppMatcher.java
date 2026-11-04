package cn.autohack.rebuild;

import java.util.List;
import java.util.Locale;

/** Resolves spoken app names without guessing when more than one installed app matches. */
public final class VoiceAppMatcher {
    public enum Status { UNIQUE, AMBIGUOUS, NO_MATCH }

    public static final class Result {
        public final Status status;
        public final StartupAppLauncher.Entry entry;

        private Result(Status status, StartupAppLauncher.Entry entry) {
            this.status = status;
            this.entry = entry;
        }
    }

    private VoiceAppMatcher() {}

    public static Result resolve(List<StartupAppLauncher.Entry> apps, String spokenTarget) {
        String target = normalize(spokenTarget);
        if (target.length() < 2 || apps == null || apps.isEmpty()) {
            return new Result(Status.NO_MATCH, null);
        }

        StartupAppLauncher.Entry exact = null;
        int exactMatches = 0;
        for (StartupAppLauncher.Entry app : apps) {
            if (app == null) continue;
            if (normalize(app.label).equals(target)
                    || normalize(app.packageName).equals(target)) {
                exact = app;
                exactMatches++;
            }
        }
        if (exactMatches == 1) return new Result(Status.UNIQUE, exact);
        if (exactMatches > 1) return new Result(Status.AMBIGUOUS, null);

        StartupAppLauncher.Entry partial = null;
        int partialMatches = 0;
        for (StartupAppLauncher.Entry app : apps) {
            if (app == null) continue;
            if (normalize(app.label).contains(target)
                    || normalize(app.packageName).contains(target)) {
                partial = app;
                partialMatches++;
            }
        }
        if (partialMatches == 1) return new Result(Status.UNIQUE, partial);
        if (partialMatches > 1) return new Result(Status.AMBIGUOUS, null);
        return new Result(Status.NO_MATCH, null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }
}
