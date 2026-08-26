package com.example.platform.outbox.coordination;

import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes tasks through the canonical bounded local sandbox boundary.
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
            if (request.workingDirectory() == null) {
                return ExecutionResult.failure(-1, "Explicit working directory is required", 0,
                        "SANDBOX_POLICY_UNSATISFIABLE", "Explicit working directory is required");
            }
            Path workspace = Path.of(request.workingDirectory()).toAbsolutePath().normalize();
            SandboxExecutionResult sandbox = LocalSandboxProcess.execute(
                    request.arguments(), workspace, workspace, Set.of(), request.environment(),
                    Duration.ofSeconds(request.timeoutSeconds()), 1L << 20, SandboxCancellation.never());
            String stdout = sandbox.stdout().utf8();
            String stderr = sandbox.stderr().utf8();
            int exitCode = sandbox.exitCode().orElse(-1);
            long dur = System.currentTimeMillis() - start;
            if (sandbox.failure().isPresent()) {
                var failure = sandbox.failure().orElseThrow();
                log.warn("Execution FAILED: task={} exitCode={} stderr={}", request.taskId(), exitCode, stderr);
                return ExecutionResult.failure(exitCode, stderr, dur, failure.code().name(), failure.message());
            }

            log.info("Execution FINISHED: task={} exitCode=0 dur={}ms", request.taskId(), dur);
            return ExecutionResult.success(exitCode, stdout, stderr, dur);
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            log.error("Execution CRASH: task={} error={}", request.taskId(), e.getMessage());
            return ExecutionResult.failure(-1, e.getMessage(), dur, "CRASH", e.getMessage());
        }
    }

}
