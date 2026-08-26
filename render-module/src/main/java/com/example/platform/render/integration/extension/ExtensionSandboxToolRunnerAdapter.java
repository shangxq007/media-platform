package com.example.platform.render.integration.extension;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.app.ToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolExecutionSafetyPolicy;
import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.SandboxProcessExecutionPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Composition adapter from extension-owned tool ports to the canonical sandbox process port. */
@Component
public final class ExtensionSandboxToolRunnerAdapter implements ProcessToolRunner, ToolRunner {
    private static final long DEFAULT_CAPTURE_BYTES = 4L * 1024 * 1024;
    private static final Map<String, String> DEFAULT_ENVIRONMENT =
            Map.of("PATH", "/usr/bin:/bin", "LANG", "C");

    private final ToolRegistry toolRegistry;
    private final SandboxProcessExecutionPort sandbox;

    public ExtensionSandboxToolRunnerAdapter(
            ToolRegistry toolRegistry, SandboxProcessExecutionPort sandbox) {
        this.toolRegistry = toolRegistry;
        this.sandbox = sandbox;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        return execute(request, ToolExecutionSafetyPolicy.defaults());
    }

    @Override
    public ToolExecutionResult execute(
            ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
        ToolExecutionSafetyPolicy effective =
                policy == null ? ToolExecutionSafetyPolicy.defaults() : policy;
        if (effective.networkAccess()) {
            throw new IllegalArgumentException("unbounded network access has no sandbox representation");
        }
        String executable = toolRegistry.resolveExecutable(request.toolKey());
        requireAllowedExecutable(executable);
        String requestedWorkingDirectory = request.workingDirectory() != null
                ? request.workingDirectory()
                : effective.workingDirectory();
        Path workspace = requireWorkspace(requestedWorkingDirectory);
        long timeoutMillis = request.timeoutMillis() > 0
                ? request.timeoutMillis()
                : effective.timeoutMillis();
        List<String> command = command(executable, request.args());
        Map<String, String> environment = new LinkedHashMap<>(request.environment());
        DEFAULT_ENVIRONMENT.forEach(environment::putIfAbsent);
        Instant started = Instant.now();
        SandboxExecutionResult result = executeSandbox(
                command, workspace, environment, timeoutMillis, effective.maxOutputBytes());
        Instant ended = Instant.now();
        int exitCode = result.exitCode().orElse(-1);
        boolean truncated = result.stdout().truncated() || result.stderr().truncated();
        String stderr = result.failure()
                .map(ExtensionSandboxToolRunnerAdapter::failureDescription)
                .orElseGet(() -> result.stderr().utf8());
        boolean timedOut = result.failure()
                .map(failure -> failure.code() == SandboxFailureCode.PROCESS_TIMEOUT)
                .orElse(false);
        return new ToolExecutionResult(
                exitCode,
                result.stdout().utf8(),
                stderr,
                timedOut,
                started,
                ended,
                Duration.between(started, ended),
                truncated);
    }

    @Override
    public ToolRunResult run(ToolRunRequest request) {
        requireAllowedExecutable(request.executable());
        if (request.timeoutMillis() <= 0) {
            throw new IllegalArgumentException("bounded timeout is required");
        }
        Path workspace = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        SandboxExecutionResult result = executeSandbox(
                command(request.executable(), request.args()),
                workspace,
                DEFAULT_ENVIRONMENT,
                request.timeoutMillis(),
                DEFAULT_CAPTURE_BYTES);
        String stderr = result.failure()
                .map(ExtensionSandboxToolRunnerAdapter::failureDescription)
                .orElseGet(() -> result.stderr().utf8());
        return new ToolRunResult(result.exitCode().orElse(-1), result.stdout().utf8(), stderr);
    }

    private SandboxExecutionResult executeSandbox(
            List<String> command,
            Path workspace,
            Map<String, String> environment,
            long timeoutMillis,
            long captureBytes) {
        try {
            return sandbox.execute(
                    command,
                    workspace,
                    workspace,
                    Set.of(),
                    environment,
                    Duration.ofMillis(timeoutMillis),
                    captureBytes,
                    SandboxCancellation.never());
        } catch (IOException failure) {
            throw new IllegalStateException("sandbox process execution failed", failure);
        }
    }

    private void requireAllowedExecutable(String executable) {
        if (!toolRegistry.isAllowedExecutable(executable)) {
            throw new IllegalArgumentException("Executable not in allowlist: " + executable);
        }
    }

    private static Path requireWorkspace(String requestedWorkingDirectory) {
        if (requestedWorkingDirectory == null || requestedWorkingDirectory.isBlank()) {
            throw new IllegalArgumentException("explicit bounded working directory is required");
        }
        Path workspace = Path.of(requestedWorkingDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("working directory does not exist: " + workspace);
        }
        return workspace;
    }

    private static List<String> command(String executable, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (arguments != null) {
            command.addAll(arguments);
        }
        return command;
    }

    private static String failureDescription(SandboxFailure failure) {
        return failure.code() + ": " + failure.message();
    }
}
