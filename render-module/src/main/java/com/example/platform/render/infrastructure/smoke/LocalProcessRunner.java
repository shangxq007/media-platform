package com.example.platform.render.infrastructure.smoke;

import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Executes a local process with strict timeout and controlled boundaries.
 *
 * <p>Uses the canonical bounded process launcher — no shell invocation.
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
            Path workspace = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
            var result = LocalSandboxProcess.execute(args, workspace, workspace, java.util.Set.of(),
                    java.util.Map.of("PATH", "/usr/bin:/bin", "LANG", "C"),
                    Duration.ofSeconds(timeoutSeconds), 1L << 20, SandboxCancellation.never());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            int exitCode = result.exitCode().orElse(-1);
            String stderr = result.failure().map(f -> f.code() + ": " + f.message())
                    .orElse(result.stderr().utf8());
            return new LocalProcessExecutionResult(result.failure().isEmpty() && exitCode == 0,
                    exitCode, elapsed, result.stdout().utf8(), stderr);
        } catch (Exception e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
            return new LocalProcessExecutionResult(false, -1, elapsed, "", e.getMessage());
        }
    }
}
