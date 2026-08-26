package com.example.platform.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Argv-only bwrap process mechanics used by both the real probe and launcher. */
final class BubblewrapProcess {
    private static final int DIAGNOSTIC_LIMIT = 16 * 1024;

    private BubblewrapProcess() {}

    static Process start(List<String> command, Map<String, String> environment) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().clear();
        builder.environment().putAll(environment);
        builder.redirectErrorStream(false);
        return builder.start();
    }

    static ProbeResult execute(List<String> command, Duration timeout) {
        Process process = null;
        try {
            process = start(command, Map.of());
            DiagnosticReader stdout = new DiagnosticReader(process.getInputStream());
            DiagnosticReader stderr = new DiagnosticReader(process.getErrorStream());
            Thread outThread = Thread.ofVirtual().start(stdout);
            Thread errThread = Thread.ofVirtual().start(stderr);
            boolean processCompleted = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            SandboxCleanupObservation cleanup = LocalBoundedProcessLauncher.terminateTree(
                    process, Duration.ofMillis(250));
            boolean capturesClosed = closeAndJoin(stdout, outThread, stderr, errThread);
            return new ProbeResult(processCompleted ? process.exitValue() : -1,
                    stdout.text(), stderr.text(), processCompleted && cleanup.completed()
                            && capturesClosed);
        } catch (IOException failure) {
            return new ProbeResult(-1, "", failure.getClass().getSimpleName(), false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ProbeResult(-1, "", "interrupted", false);
        } finally {
            if (process != null && process.isAlive()) {
                LocalBoundedProcessLauncher.terminateTree(process, Duration.ofMillis(250));
            }
            if (process != null) {
                close(process.getInputStream());
                close(process.getErrorStream());
                close(process.getOutputStream());
            }
        }
    }

    private static boolean closeAndJoin(
            DiagnosticReader stdout, Thread outThread,
            DiagnosticReader stderr, Thread errThread) throws InterruptedException {
        outThread.join(500);
        errThread.join(500);
        stdout.close();
        stderr.close();
        outThread.join(500);
        errThread.join(500);
        return !outThread.isAlive() && !errThread.isAlive();
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    record ProbeResult(int exitCode, String stdout, String stderr, boolean completed) {
        boolean succeeded() {
            return completed && exitCode == 0;
        }

        String diagnostic() {
            String value = stderr.isBlank() ? stdout : stderr;
            return value.replaceAll("[\\r\\n]+", " ").trim();
        }
    }

    private static final class DiagnosticReader implements Runnable, AutoCloseable {
        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private DiagnosticReader(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    int remaining = DIAGNOSTIC_LIMIT - captured.size();
                    if (remaining > 0) captured.write(buffer, 0, Math.min(remaining, read));
                }
            } catch (IOException ignored) {
            }
        }

        private String text() {
            return captured.toString(StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
