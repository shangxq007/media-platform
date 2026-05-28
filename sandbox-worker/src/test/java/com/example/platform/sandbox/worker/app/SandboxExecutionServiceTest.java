package com.example.platform.sandbox.worker.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.sandbox.worker.config.SandboxWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SandboxExecutionServiceTest {

    private SandboxExecutionService executionService;

    @BeforeEach
    void setUp() {
        SandboxWorkerProperties props = new SandboxWorkerProperties(
                5, 1024 * 1024, 65536, 500, java.util.List.of("python", "py"));
        executionService = new SandboxExecutionService(props);
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
}
