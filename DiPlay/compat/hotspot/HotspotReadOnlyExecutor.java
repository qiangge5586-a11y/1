package com.shilapi.xcertplay.network.safety;

/**
 * Execution boundary for diagnostics. Production code must inject a reviewed
 * argv-based runner; tests inject a fake runner. No shell implementation lives here.
 */
public final class HotspotReadOnlyExecutor {
    public interface Runner {
        RunResult run(String executable, String[] arguments, long timeoutMillis);
    }

    public static final class RunResult {
        public final int exitCode;
        public final boolean timedOut;
        public final String stdout;
        public final String stderr;
        public RunResult(int exitCode, boolean timedOut, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }

    public static final class Result {
        public final HotspotReadOnlyOperation.Kind kind;
        public final String output;
        private Result(HotspotReadOnlyOperation.Kind kind, String output) {
            this.kind = kind;
            this.output = output;
        }
    }

    private final Runner runner;
    private final long timeoutMillis;
    private final int maxOutputChars;

    public HotspotReadOnlyExecutor(Runner runner, long timeoutMillis, int maxOutputChars) {
        if (runner == null) throw new IllegalArgumentException("runner required");
        if (timeoutMillis < 100L || timeoutMillis > 30000L)
            throw new IllegalArgumentException("timeout outside safe bounds");
        if (maxOutputChars < 128 || maxOutputChars > 262144)
            throw new IllegalArgumentException("output limit outside safe bounds");
        this.runner = runner;
        this.timeoutMillis = timeoutMillis;
        this.maxOutputChars = maxOutputChars;
    }

    public Result execute(HotspotReadOnlyOperation.Kind kind) {
        HotspotReadOnlyOperation op = HotspotReadOnlyOperation.forKind(kind);
        String[] argv = op.getArguments();
        RunResult raw = runner.run(op.executable, argv, timeoutMillis);
        if (raw == null) throw new IllegalStateException("runner returned no result");
        if (raw.timedOut) throw new IllegalStateException("read-only command timed out");
        if (raw.exitCode != 0) throw new IllegalStateException("read-only command failed: " + raw.exitCode);
        if (raw.stdout.length() > maxOutputChars || raw.stderr.length() > maxOutputChars)
            throw new IllegalStateException("read-only command output exceeded limit");
        return new Result(kind, raw.stdout);
    }
}
