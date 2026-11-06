package com.shilapi.xcertplay.network.safety;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Android 4.2/API 17 su-backed runner for the fixed read-only diagnostic catalog.
 * It deliberately rejects arbitrary executables and arguments. No write command
 * is present in HotspotReadOnlyOperation and this class cannot perform mutations.
 */
public final class Api17SuReadOnlyRunner implements HotspotReadOnlyExecutor.Runner {
    private static final int MAX_CAPTURE_BYTES = 262144;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public HotspotReadOnlyExecutor.RunResult run(String executable, String[] arguments,
            long timeoutMillis) {
        if (timeoutMillis < 100L || timeoutMillis > 30000L)
            throw new IllegalArgumentException("timeout outside safe bounds");
        String command = requireCatalogCommand(executable, arguments);
        Process process = null;
        StreamCollector stdout = null;
        StreamCollector stderr = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            stdout = new StreamCollector(process.getInputStream());
            stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();
            Waiter waiter = new Waiter(process);
            waiter.start();
            waiter.join(timeoutMillis);
            if (waiter.isAlive()) {
                process.destroy();
                waiter.join(1000L);
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
                stdout.join(1000L);
                stderr.join(1000L);
                return new HotspotReadOnlyExecutor.RunResult(-1, true,
                        stdout.text(), stderr.text());
            }
            stdout.join(1000L);
            stderr.join(1000L);
            if (stdout.isAlive() || stderr.isAlive())
                throw new IllegalStateException("diagnostic output stream did not close");
            if (stdout.overflow || stderr.overflow)
                throw new IllegalStateException("diagnostic output exceeded capture limit");
            return new HotspotReadOnlyExecutor.RunResult(waiter.exitCode, false,
                    stdout.text(), stderr.text());
        } catch (InterruptedException e) {
            if (process != null) process.destroy();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("diagnostic command interrupted", e);
        } catch (IOException e) {
            if (process != null) process.destroy();
            throw new IllegalStateException("unable to start su diagnostic command", e);
        } finally {
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    static String requireCatalogCommand(String executable, String[] arguments) {
        if (executable == null || arguments == null)
            throw new IllegalArgumentException("command required");
        for (HotspotReadOnlyOperation.Kind kind : HotspotReadOnlyOperation.Kind.values()) {
            HotspotReadOnlyOperation op = HotspotReadOnlyOperation.forKind(kind);
            if (!op.executable.equals(executable)) continue;
            String[] expected = op.getArguments();
            if (same(expected, arguments)) return quote(executable) + appendQuoted(arguments);
        }
        throw new SecurityException("command is not in the read-only allowlist");
    }

    private static boolean same(String[] a, String[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!a[i].equals(b[i])) return false;
        return true;
    }

    private static String appendQuoted(String[] args) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.length; i++) out.append(' ').append(quote(args[i]));
        return out.toString();
    }

    /** POSIX single-quote escaping; input is still restricted to the exact catalog. */
    static String quote(String value) {
        if (value == null || value.indexOf('\u0000') >= 0)
            throw new IllegalArgumentException("invalid shell argument");
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (IOException ignored) { }
    }

    private static final class Waiter extends Thread {
        private final Process process;
        volatile int exitCode = -1;
        Waiter(Process process) { this.process = process; setDaemon(true); }
        public void run() {
            try { exitCode = process.waitFor(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static final class StreamCollector extends Thread {
        private final InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        volatile boolean overflow;
        StreamCollector(InputStream input) { this.input = input; setDaemon(true); }
        public void run() {
            byte[] buffer = new byte[4096];
            try {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int remaining = MAX_CAPTURE_BYTES - bytes.size();
                    if (remaining > 0) bytes.write(buffer, 0, Math.min(count, remaining));
                    if (count > remaining) overflow = true;
                }
            } catch (IOException ignored) { }
        }
        synchronized String text() { return new String(bytes.toByteArray(), UTF8); }
    }
}
