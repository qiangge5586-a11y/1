import com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor;
import com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor.RunResult;
import com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor.Result;
import com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation.Kind;

public final class HotspotReadOnlyExecutorTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable r, String message) {
        try { r.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    private static void fails(Runnable r, String message) {
        try { r.run(); throw new AssertionError(message); }
        catch (IllegalStateException expected) { }
    }
    private static final class FakeRunner implements HotspotReadOnlyExecutor.Runner {
        String executable;
        String[] arguments;
        long timeout;
        RunResult next;
        public RunResult run(String exe, String[] argv, long timeoutMillis) {
            executable = exe;
            arguments = argv.clone();
            timeout = timeoutMillis;
            return next;
        }
    }
    public static void main(String[] args) {
        final FakeRunner fake = new FakeRunner();
        fake.next = new RunResult(0, false, "uid=0(root) gid=0(root)\n", "");
        final HotspotReadOnlyExecutor executor = new HotspotReadOnlyExecutor(fake, 3000L, 4096);
        Result result = executor.execute(Kind.ROOT_IDENTITY);
        check(result.output.contains("uid=0"), "fixture output returned");
        check("/system/bin/id".equals(fake.executable), "only fixed executable passed");
        check(fake.arguments.length == 0, "no caller arguments passed");
        check(fake.timeout == 3000L, "bounded timeout passed");

        fake.next = new RunResult(0, false, "wifi status fixture", "");
        executor.execute(Kind.WIFI_SERVICE_STATE);
        check("/system/bin/dumpsys".equals(fake.executable), "wifi uses fixed diagnostic binary");
        check(fake.arguments.length == 1 && "wifi".equals(fake.arguments[0]),
                "wifi service argument fixed");

        fake.next = new RunResult(0, true, "", "");
        fails(new Runnable() { public void run() {
            executor.execute(Kind.ROOT_IDENTITY);
        }}, "timeout must fail closed");

        fake.next = new RunResult(2, false, "", "permission denied");
        fails(new Runnable() { public void run() {
            executor.execute(Kind.ROOT_IDENTITY);
        }}, "nonzero exit must fail closed");

        fake.next = new RunResult(0, false, new String( new char[300] ).replace('\0', 'x'), "");
        final HotspotReadOnlyExecutor small = new HotspotReadOnlyExecutor(fake, 3000L, 256);
        fails(new Runnable() { public void run() {
            small.execute(Kind.ROOT_IDENTITY);
        }}, "oversized output must fail closed");

        rejects(new Runnable() { public void run() {
            new HotspotReadOnlyExecutor(fake, 10L, 1024);
        }}, "too-short timeout rejected");
        rejects(new Runnable() { public void run() {
            new HotspotReadOnlyExecutor(fake, 3000L, 10);
        }}, "too-small output limit rejected");

        System.out.println("PASS: fake read-only executor, fixed argv, timeout and output fail-closed");
    }
}
