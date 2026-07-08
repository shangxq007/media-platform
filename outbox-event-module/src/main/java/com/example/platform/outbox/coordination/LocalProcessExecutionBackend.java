package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes tasks as local subprocesses via ProcessBuilder.
 * Used for ffprobe, ffmpeg, and other CLI-based tasks.
 */
@Component
public class LocalProcessExecutionBackend implements ExecutionBackend {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessExecutionBackend.class);

    @Override
    public String backendId() {
        return "local-process";
    }

    @Override
    public boolean supports(TaskCapability capability) {
        return capability == TaskCapability.PROBE || capability == TaskCapability.ASR
                || capability == TaskCapability.OCR || capability == TaskCapability.VISION
                || capability == TaskCapability.EMBEDDING;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        long start = System.currentTimeMillis();
        log.info("Execution STARTED: backend=local-process task={} cap={} args={}",
                request.taskId(), request.taskCapability(), request.arguments());

        try {
            ProcessBuilder pb = new ProcessBuilder(request.arguments());
            if (request.workingDirectory() != null) {
                pb.directory(new java.io.File(request.workingDirectory()));
            }
            pb.environment().putAll(request.environment());
            pb.redirectErrorStream(false);

            Process proc = pb.start();
            String stdout = readStream(proc.getInputStream());
            String stderr = readStream(proc.getErrorStream());

            boolean finished = proc.waitFor(request.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                long dur = System.currentTimeMillis() - start;
                log.warn("Execution TIMEOUT: task={} after {}s", request.taskId(), request.timeoutSeconds());
                return ExecutionResult.failure(-1, "Timeout after " + request.timeoutSeconds() + "s",
                        dur, "TIMEOUT", "Process timed out");
            }

            int exitCode = proc.exitValue();
            long dur = System.currentTimeMillis() - start;
            if (exitCode != 0) {
                log.warn("Execution FAILED: task={} exitCode={} stderr={}", request.taskId(), exitCode, stderr);
                return ExecutionResult.failure(exitCode, stderr, dur, "NONZERO_EXIT", "Exit code: " + exitCode);
            }

            log.info("Execution FINISHED: task={} exitCode=0 dur={}ms", request.taskId(), dur);
            return ExecutionResult.success(exitCode, stdout, stderr, dur);
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            log.error("Execution CRASH: task={} error={}", request.taskId(), e.getMessage());
            return ExecutionResult.failure(-1, e.getMessage(), dur, "CRASH", e.getMessage());
        }
    }

    private String readStream(InputStream is) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().reduce("", (a, b) -> a + b + "\n");
        }
    }
}
