package com.example.platform.testsupport;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Test-only policy for portable versus authoritative Phase 17 conformance execution. */
public final class Phase17SandboxConformance {
    public static final String REQUIRED_ENVIRONMENT =
            "MEDIA_PLATFORM_REQUIRE_PHASE17_SANDBOX_CONFORMANCE";
    private static final int DIAGNOSTIC_LIMIT = 8 * 1024;
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(45);

    private Phase17SandboxConformance() {}

    public static boolean isRequired() {
        return "true".equals(System.getenv(REQUIRED_ENVIRONMENT));
    }

    public static void requireCapability(boolean available, String diagnostic) {
        if (available) {
            return;
        }
        if (isRequired()) {
            fail(diagnostic);
        }
        assumeTrue(false, diagnostic);
    }

    public static void requireSuccessfulProcess(List<String> command, String capability) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Process runningProcess = process;
            Thread reader = Thread.ofVirtual().start(() -> copyBounded(runningProcess, output));
            boolean completed = process.waitFor(
                    PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(5_000);
            requireCapability(completed && process.exitValue() == 0 && !reader.isAlive(),
                    capability + " unavailable or unsuccessful: "
                            + boundedDiagnostic(output));
        } catch (IOException failure) {
            requireCapability(false,
                    capability + " unavailable: " + failure.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail(capability + " check interrupted", interrupted);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void copyBounded(Process process, ByteArrayOutputStream output) {
        byte[] buffer = new byte[1024];
        try (var input = process.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = DIAGNOSTIC_LIMIT - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(remaining, read));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String boundedDiagnostic(ByteArrayOutputStream output) {
        String diagnostic = output.toString(StandardCharsets.UTF_8)
                .replaceAll("[\\r\\n]+", " ").trim();
        return diagnostic.isEmpty() ? "no bounded process diagnostic" : diagnostic;
    }
}
