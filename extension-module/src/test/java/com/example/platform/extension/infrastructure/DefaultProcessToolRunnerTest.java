package com.example.platform.extension.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.ToolCapability;
import com.example.platform.extension.domain.ToolDefinition;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultProcessToolRunnerTest {

    private ToolRegistry registry;
    private DefaultProcessToolRunner runner;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        runner = new DefaultProcessToolRunner(registry);
    }

    @Test
    void shouldExecuteAllowedTool() {
        // Register 'echo' which is available on all Unix systems
        registry.registerExecutable("echo", "/usr/bin/echo");
        registry.registerTool(new ToolDefinition(
                "echo", "Echo", "Echo command",
                "/usr/bin/echo",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.of("echo", List.of("hello", "world"));
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
        assertTrue(result.stdout().contains("hello world"));
        assertFalse(result.timedOut());
        assertNotNull(result.startTime());
        assertNotNull(result.endTime());
    }

    @Test
    void shouldCaptureStderr() {
        // Use 'ls' on a nonexistent path to generate stderr
        registry.registerExecutable("ls", "/usr/bin/ls");
        registry.registerTool(new ToolDefinition(
                "ls", "List", "List directory",
                "/usr/bin/ls",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.of("ls", List.of("/nonexistent_path_xyz"));
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertNotEquals(0, result.exitCode());
        assertFalse(result.isSuccess());
        assertFalse(result.stderr().isBlank());
    }

    @Test
    void shouldHandleNonZeroExitCode() {
        // 'false' command always exits with code 1
        registry.registerExecutable("false", "/usr/bin/false");
        registry.registerTool(new ToolDefinition(
                "false", "False", "Always fails",
                "/usr/bin/false",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.of("false", List.of());
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertEquals(1, result.exitCode());
        assertFalse(result.isSuccess());
        assertFalse(result.timedOut());
    }

    @Test
    void shouldRejectUnregisteredTool() {
        ToolExecutionRequest request = ToolExecutionRequest.of("nonexistent", List.of("arg"));
        assertThrows(IllegalArgumentException.class, () -> runner.execute(request));
    }

    @Test
    void shouldRejectExecutableNotInAllowlist() {
        // Register a tool, then try to execute with a different path
        registry.registerExecutable("mytool", "/usr/bin/echo");
        registry.registerTool(new ToolDefinition(
                "mytool", "My Tool", "Test tool",
                "/usr/bin/echo",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        // Directly create a request for a tool key that doesn't exist
        ToolExecutionRequest request = ToolExecutionRequest.of("not_in_allowlist", List.of("arg"));
        assertThrows(IllegalArgumentException.class, () -> runner.execute(request));
    }

    @Test
    void shouldEnforceTimeout() {
        // 'sleep 10' with a 1-second timeout
        registry.registerExecutable("sleep", "/usr/bin/sleep");
        registry.registerTool(new ToolDefinition(
                "sleep", "Sleep", "Sleep command",
                "/usr/bin/sleep",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout("sleep", List.of("10"), 1000);
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertTrue(result.timedOut());
        assertFalse(result.isSuccess());
    }

    @Test
    void shouldCaptureStdout() {
        registry.registerExecutable("echo", "/usr/bin/echo");
        registry.registerTool(new ToolDefinition(
                "echo", "Echo", "Echo command",
                "/usr/bin/echo",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.of("echo", List.of("test_output"));
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertTrue(result.stdout().contains("test_output"));
    }

    @Test
    void shouldHandleEmptyArgs() {
        registry.registerExecutable("true", "/usr/bin/true");
        registry.registerTool(new ToolDefinition(
                "true", "True", "Always succeeds",
                "/usr/bin/true",
                List.of(new ToolCapability("test", "Test capability")),
                ToolSandboxPolicy.defaults()));

        ToolExecutionRequest request = ToolExecutionRequest.of("true", List.of());
        ToolExecutionResult result = runner.execute(request);

        assertNotNull(result);
        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
    }
}
