package com.example.platform.extension.infrastructure;

import com.example.platform.extension.app.ToolRunner;
import com.example.platform.extension.domain.ToolRunRequest;
import com.example.platform.extension.domain.ToolRunResult;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runs a CLI tool with Apache Commons Exec; captures stdout/stderr (bounded) and honors timeouts.
 */
@Component
public class CommonsExecToolRunner implements ToolRunner {

    /** Per-stream capture cap to avoid OOM on chatty tools. */
    private static final int MAX_STDOUT_STDERR_BYTES = 4 * 1024 * 1024;

    @Override
    public ToolRunResult run(ToolRunRequest request) {
        CommandLine commandLine = new CommandLine(request.executable());
        List<String> commandArgs = request.args();
        if (commandArgs != null && !commandArgs.isEmpty()) {
            commandLine.addArguments(commandArgs.toArray(String[]::new), false);
        }

        var stdoutCapture = new LimitedByteArrayOutputStream(MAX_STDOUT_STDERR_BYTES);
        var stderrCapture = new LimitedByteArrayOutputStream(MAX_STDOUT_STDERR_BYTES);

        DefaultExecutor commandExecutor = new DefaultExecutor();
        commandExecutor.setStreamHandler(
                new PumpStreamHandler(stdoutCapture, stderrCapture, InputStream.nullInputStream()));

        long timeoutMillis = request.timeoutMillis();
        if (timeoutMillis > 0L) {
            commandExecutor.setWatchdog(new ExecuteWatchdog(timeoutMillis));
        }

        int exitCode;
        try {
            exitCode = commandExecutor.execute(commandLine);
        } catch (ExecuteException e) {
            exitCode = e.getExitValue();
        } catch (IOException e) {
            throw new IllegalStateException("failed to execute: " + request.executable(), e);
        }

        String stdoutText = toUtf8String(stdoutCapture);
        String stderrText = toUtf8String(stderrCapture);
        if (stdoutCapture.isTruncated()) {
            stdoutText = stdoutText + "\n[stdout truncated at " + MAX_STDOUT_STDERR_BYTES + " bytes]";
        }
        if (stderrCapture.isTruncated()) {
            stderrText = stderrText + "\n[stderr truncated at " + MAX_STDOUT_STDERR_BYTES + " bytes]";
        }
        return new ToolRunResult(exitCode, stdoutText, stderrText);
    }

    private static String toUtf8String(LimitedByteArrayOutputStream captureStream) {
        return new String(captureStream.toByteArray(), StandardCharsets.UTF_8);
    }
}
