package com.example.platform.render.infrastructure.smoke;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Executes a local process with strict timeout and controlled boundaries.
 *
 * <p>Uses {@link ProcessBuilder} directly — no shell invocation.
 * Captures stdout, stderr, and exit code.</p>
 */
public final class LocalProcessRunner {

    private LocalProcessRunner() {}

    /**
     * Result of executing a local process.
     */
    public record LocalProcessExecutionResult(
            boolean success,
            int exitCode,
            Duration duration,
            String stdout,
            String stderr
    ) {}

    /**
     * Executes a command with timeout.
     *
     * @param args          command arguments (first element is the binary)
     * @param timeoutSeconds maximum execution time in seconds
     * @return execution result
     */
    public static LocalProcessExecutionResult execute(List<String> args, int timeoutSeconds) {
        Objects.requireNonNull(args, "args must not be null");
        if (args.isEmpty()) throw new IllegalArgumentException("args must not be empty");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be positive");

        long startNanos = System.nanoTime();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(false);

            Process p = pb.start();

            byte[] stdoutBytes = p.getInputStream().readAllBytes();
            byte[] stderrBytes = p.getErrorStream().readAllBytes();

            boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

            if (!done) {
                p.destroyForcibly();
                return new LocalProcessExecutionResult(
                        false, -1, elapsed,
                        new String(stdoutBytes),
                        "PROCESS_TIMEOUT after " + timeoutSeconds + "s");
            }

            String stdout = new String(stdoutBytes);
            String stderr = new String(stderrBytes);
            int exitCode = p.exitValue();

            return new LocalProcessExecutionResult(exitCode == 0, exitCode, elapsed, stdout, stderr);
        } catch (Exception e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return new LocalProcessExecutionResult(false, -1, elapsed, "", e.getMessage());
        }
    }
}
