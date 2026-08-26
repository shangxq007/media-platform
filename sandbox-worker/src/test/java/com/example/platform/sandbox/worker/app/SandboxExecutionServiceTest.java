package com.example.platform.sandbox.worker.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.sandbox.worker.config.SandboxWorkerProperties;
import com.example.platform.sandbox.BoundedCapture;
import com.example.platform.sandbox.SandboxCleanupObservation;
import com.example.platform.sandbox.SandboxExecutionHandle;
import com.example.platform.sandbox.SandboxExecutionObservation;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SandboxExecutionServiceTest {

    private SandboxExecutionService executionService;

    @BeforeEach
    void setUp() {
        SandboxWorkerProperties props = new SandboxWorkerProperties(
                5, 1024 * 1024, 65536, 500, java.util.List.of("python", "py"));
        executionService = new SandboxExecutionService(props,
                (command, workspace, workingDirectory, readOnlyInputs, exactEnvironment,
                        timeout, captureBytes, cancellation) -> fakeExecution(
                                Files.readString(Path.of(command.get(1))), workingDirectory,
                                captureBytes));
    }

    @Test
    void pythonHelloWorldReturnsSuccess() {
        SandboxExecutionResult result = executionService.execute(
                "python", "print('hello world')", 5000);

        assertEquals(SandboxExecutionResult.Status.SUCCESS, result.status());
        assertTrue(result.stdout().contains("hello world"));
        assertEquals(0, result.exitCode());
    }

    @Test
    void pythonWithInputOutput() {
        SandboxExecutionResult result = executionService.execute(
                "python", "import sys; print(sys.version)", 5000);

        assertEquals(SandboxExecutionResult.Status.SUCCESS, result.status());
        assertFalse(result.stdout().isBlank());
    }

    @Test
    void pythonSyntaxErrorReturnsFailed() {
        SandboxExecutionResult result = executionService.execute(
                "python", "def foo(\n  pass", 5000);

        assertEquals(SandboxExecutionResult.Status.FAILED, result.status());
        assertNotEquals(0, result.exitCode());
    }

    @Test
    void pythonInfiniteLoopReturnsTimeout() {
        SandboxExecutionResult result = executionService.execute(
                "python", "while True: pass", 500);

        assertEquals(SandboxExecutionResult.Status.TIMEOUT, result.status());
    }

    @Test
    void unsupportedLanguageReturnsDenied() {
        SandboxExecutionResult result = executionService.execute(
                "rust", "fn main() {}", 5000);

        assertEquals(SandboxExecutionResult.Status.DENIED, result.status());
        assertTrue(result.message().contains("not supported"));
    }

    @Test
    void blankCodeReturnsDenied() {
        SandboxExecutionResult result = executionService.execute("python", "", 5000);

        assertEquals(SandboxExecutionResult.Status.DENIED, result.status());
    }

    @Test
    void nullLanguageReturnsDenied() {
        SandboxExecutionResult result = executionService.execute(null, "code", 5000);

        assertEquals(SandboxExecutionResult.Status.DENIED, result.status());
    }

    @Test
    void outputTruncatedWhenTooLong() {
        // Generate very large output
        SandboxExecutionResult result = executionService.execute(
                "python", "print('x' * 2000000)", 10000);

        assertTrue(result.truncated() || result.stdout().length() <= 1024 * 1024 + 100,
                "Output should be truncated to maxOutputBytes");
    }

    @Test
    void pyLanguageAliasWorks() {
        SandboxExecutionResult result = executionService.execute(
                "py", "print('hello')", 5000);

        assertEquals(SandboxExecutionResult.Status.SUCCESS, result.status());
    }

    private com.example.platform.sandbox.SandboxExecutionResult fakeExecution(
            String code, Path workingDirectory, long captureBytes) {
        if (code.contains("while True")) {
            return result(OptionalInt.empty(), "", "",
                    Optional.of(SandboxFailure.of(SandboxFailureCode.PROCESS_TIMEOUT,
                            "process exceeded wall-clock timeout", Set.of())), false,
                    workingDirectory);
        }
        if (code.contains("def foo(")) {
            return result(OptionalInt.of(1), "", "syntax error",
                    Optional.of(SandboxFailure.of(SandboxFailureCode.PROCESS_CRASHED,
                            "process exited non-zero", Set.of())), false, workingDirectory);
        }
        String stdout = code.contains("sys.version") ? "3.13-test\n"
                : code.contains("2000000") ? "x".repeat(Math.toIntExact(captureBytes))
                : code.contains("hello world") ? "hello world\n" : "hello\n";
        return result(OptionalInt.of(0), stdout, "", Optional.empty(),
                code.contains("2000000"), workingDirectory);
    }

    private com.example.platform.sandbox.SandboxExecutionResult result(
            OptionalInt exitCode,
            String stdout,
            String stderr,
            Optional<SandboxFailure> failure,
            boolean truncated,
            Path workingDirectory) {
        Instant now = Instant.now();
        return new com.example.platform.sandbox.SandboxExecutionResult(exitCode,
                new BoundedCapture(stdout.getBytes(StandardCharsets.UTF_8), truncated),
                new BoundedCapture(stderr.getBytes(StandardCharsets.UTF_8), false), failure,
                new SandboxExecutionObservation(new SandboxExecutionHandle(42, now),
                        workingDirectory, Duration.ZERO,
                        new SandboxCleanupObservation(true, 0, List.of(), "")));
    }
}
