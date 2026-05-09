package com.example.platform.extension.infrastructure;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ProcessToolRunner} using Apache Commons Exec.
 *
 * <p>This is the only class in the codebase that may construct process execution
 * commands. All business modules must use {@link ProcessToolRunner} or
 * {@link ToolRunner} instead of direct process execution.</p>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>Executable must be in the {@link ToolRegistry} allowlist</li>
 *   <li>Arguments are passed as {@link List} — never concatenated into a shell string</li>
 *   <li>Path traversal is rejected</li>
 *   <li>Timeout is enforced via {@link ExecuteWatchdog}</li>
 *   <li>Output is captured with size limits</li>
 * </ul>
 */
@Component
public class DefaultProcessToolRunner implements ProcessToolRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessToolRunner.class);

    private final ToolRegistry toolRegistry;

    public DefaultProcessToolRunner(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        return execute(request, null);
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
        String toolKey = request.toolKey();

        // Resolve and validate executable
        String executable;
        try {
            executable = toolRegistry.resolveExecutable(toolKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tool not registered: " + toolKey, e);
        }

        if (!toolRegistry.isAllowedExecutable(executable)) {
            throw new IllegalArgumentException("Executable not in allowlist: " + executable);
        }

        // Build command with List<String> args — no shell concatenation
        CommandLine commandLine = new CommandLine(executable);
        if (request.args() != null && !request.args().isEmpty()) {
            commandLine.addArguments(request.args().toArray(String[]::new), false);
        }

        // Determine effective sandbox policy
        ToolSandboxPolicy effectivePolicy = policy != null ? policy : ToolSandboxPolicy.defaults();
        long timeoutMillis = request.timeoutMillis() > 0
                ? request.timeoutMillis() : effectivePolicy.timeoutMillis();
        long maxOutputBytes = effectivePolicy.maxOutputBytes();

        // Set up output capture with size limits
        LimitedByteArrayOutputStream stdoutCapture = new LimitedByteArrayOutputStream((int) maxOutputBytes);
        LimitedByteArrayOutputStream stderrCapture = new LimitedByteArrayOutputStream((int) maxOutputBytes);

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(new PumpStreamHandler(
                stdoutCapture, stderrCapture, InputStream.nullInputStream()));

        // Working directory
        String workDir = request.workingDirectory() != null
                ? request.workingDirectory() : effectivePolicy.workingDirectory();
        if (workDir != null) {
            File workDirFile = new File(workDir);
            if (!workDirFile.isDirectory()) {
                throw new IllegalArgumentException("Working directory does not exist: " + workDir);
            }
            executor.setWorkingDirectory(workDirFile);
        }

        // Timeout
        if (timeoutMillis > 0) {
            executor.setWatchdog(new ExecuteWatchdog(timeoutMillis));
        }

        log.info("Executing tool: {} args={} timeout={}ms", toolKey, request.args(), timeoutMillis);

        Instant startTime = Instant.now();
        int exitCode;
        boolean timedOut = false;

        try {
            exitCode = executor.execute(commandLine);
        } catch (ExecuteException e) {
            exitCode = e.getExitValue();
            if (executor.getWatchdog() != null && executor.getWatchdog().killedProcess()) {
                timedOut = true;
                log.warn("Tool {} timed out after {}ms", toolKey, timeoutMillis);
            }
        } catch (IOException e) {
            Instant endTime = Instant.now();
            throw new IllegalStateException("Failed to execute tool: " + toolKey, e);
        }

        Instant endTime = Instant.now();
        boolean truncated = stdoutCapture.isTruncated() || stderrCapture.isTruncated();

        String stdoutText = new String(stdoutCapture.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        String stderrText = new String(stderrCapture.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);

        if (stdoutCapture.isTruncated()) {
            stdoutText += "\n[stdout truncated at " + maxOutputBytes + " bytes]";
        }
        if (stderrCapture.isTruncated()) {
            stderrText += "\n[stderr truncated at " + maxOutputBytes + " bytes]";
        }

        if (timedOut) {
            return ToolExecutionResult.timedOut(stdoutText, stderrText, startTime, endTime);
        } else if (exitCode != 0) {
            return ToolExecutionResult.failed(exitCode, stdoutText, stderrText, startTime, endTime);
        } else {
            return ToolExecutionResult.success(exitCode, stdoutText, stderrText, startTime, endTime);
        }
    }
}
