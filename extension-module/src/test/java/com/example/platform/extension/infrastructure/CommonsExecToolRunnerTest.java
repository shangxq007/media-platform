package com.example.platform.extension.infrastructure;

import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommonsExecToolRunnerTest {

    private final CommonsExecToolRunner runner = new CommonsExecToolRunner();

    @Test
    void runWithEchoCapturesStdout() {
        ToolRunRequest request = new ToolRunRequest("/bin/echo", List.of("hello"), 10_000L);
        ToolRunResult result = runner.run(request);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
        assertTrue(result.stderr().isBlank());
    }

    @Test
    void runWithNullArgsUsesEmptyList() {
        ToolRunRequest request = new ToolRunRequest("/bin/echo", null, 10_000L);
        ToolRunResult result = runner.run(request);

        assertEquals(0, result.exitCode());
    }

    @Test
    void runWithEmptyArgsExecutesExecutableOnly() {
        ToolRunRequest request = new ToolRunRequest("/bin/echo", List.of(), 10_000L);
        ToolRunResult result = runner.run(request);

        assertEquals(0, result.exitCode());
    }

    @Test
    void runWithFalseCommandReturnsNonZeroExitCode() {
        ToolRunRequest request = new ToolRunRequest("/bin/false", List.of(), 10_000L);
        ToolRunResult result = runner.run(request);

        assertNotEquals(0, result.exitCode());
    }

    @Test
    void runWithMultipleArgsPassesAllToProcess() {
        ToolRunRequest request = new ToolRunRequest(
                "/bin/echo", List.of("a", "b", "c"), 10_000L);
        ToolRunResult result = runner.run(request);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("a"));
        assertTrue(result.stdout().contains("b"));
        assertTrue(result.stdout().contains("c"));
    }

    @Test
    void runWithNonExistentExecutableThrowsIllegalStateException() {
        ToolRunRequest request = new ToolRunRequest(
                "/nonexistent/binary/that/does/not/exist", List.of(), 10_000L);
        assertThrows(IllegalStateException.class, () -> runner.run(request));
    }
}
