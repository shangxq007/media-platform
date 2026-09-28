package com.example.platform.coverimage;

import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.BubblewrapSandboxDetection;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailureCode;
import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.ExecutionResult;
import com.example.platform.sandbox.execution.TaskCapability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sandbox execution backend for the cover-image capability: the registered provider's command runs
 * inside a bubblewrap profile with the pinned FFmpeg binary. No shell is involved and no other
 * capability is accepted.
 *
 * <p>Mount profile (COVER-PROVIDER-001 defect 3): everything is read-only except the worker's own
 * output root, which is bound read-write at the same path so the produced cover bytes are visible to
 * the parent process. {@code /tmp} is a private tmpfs and therefore cannot carry the provider output:
 * binding only {@code /tmp} (or binding nothing writable) leaves the provider unable to hand its
 * bytes back.
 *
 * <p>Process mechanics (argv launch, bounded capture, wall-clock timeout and process-tree
 * termination) are owned by {@code sandbox-isolation-module}: this backend composes the exact
 * bubblewrap profile and hands the prepared argv to the module's canonical
 * {@link com.example.platform.sandbox.BubblewrapSandboxProcessLauncher#launchPreparedCommand
 * launchPreparedCommand} entry, so process spawning never leaves the sandbox boundary. There is no
 * fallback: if the real, probed bubblewrap sandbox is unavailable the backend fails closed instead of
 * executing the provider on the host.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class CoverImageExecutionBackend implements ExecutionBackend {

    private static final long CAPTURE_BYTES = 1L << 20;

    private final String bwrap;
    private final String ffmpeg;
    private final Path outputRoot;

    public CoverImageExecutionBackend(
            @Value("${platform.cover-image.sandbox.bwrap:/usr/bin/bwrap}") String bwrap,
            @Value("${platform.cover-image.sandbox.ffmpeg:/usr/bin/ffmpeg}") String ffmpeg,
            @Value("${app.cover-image.work-root:./.data/cover-image-work}") String workRoot) {
        this.bwrap = bwrap;
        this.ffmpeg = ffmpeg;
        this.outputRoot = Path.of(workRoot).toAbsolutePath().normalize();
    }

    @Override
    public String backendId() {
        return "cover-image-sandbox";
    }

    @Override
    public boolean supports(TaskCapability capability) {
        return capability == TaskCapability.COVER_IMAGE;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        long started = System.nanoTime();
        try {
            // bubblewrap requires the bind source to exist before the namespace is created.
            Files.createDirectories(outputRoot);
        } catch (IOException failure) {
            throw new IllegalStateException("cover-image sandbox output root is unusable", failure);
        }
        List<String> command = new ArrayList<>(List.of(
                bwrap,
                "--ro-bind", "/", "/",
                "--dev", "/dev",
                "--proc", "/proc",
                "--tmpfs", "/tmp",
                // Host-readable writable output root: the provider writes here and the parent reads it.
                "--bind", outputRoot.toString(), outputRoot.toString(),
                "--unshare-all",
                "--die-with-parent",
                ffmpeg));
        command.addAll(request.arguments());

        BubblewrapSandboxDetection detection = BubblewrapSandboxCapabilityDetector.detect();
        if (detection.launcher().isEmpty()) {
            return failed(started, "EXECUTION_UNAVAILABLE",
                    "cover-image sandbox is unavailable: " + detection.diagnostic());
        }
        SandboxExecutionResult result;
        try {
            result = detection.launcher().orElseThrow().launchPreparedCommand(
                    command, outputRoot, Duration.ofSeconds(request.timeoutSeconds()), CAPTURE_BYTES,
                    SandboxCancellation.never());
        } catch (IOException | IllegalArgumentException failure) {
            return failed(started, "EXECUTION_FAILED", "cover-image sandbox launch failed");
        }

        long duration = (System.nanoTime() - started) / 1_000_000L;
        String stdout = result.stdout().utf8();
        String stderr = result.stderr().utf8();
        int exit = result.exitCode().orElse(-1);
        List<String> outputs = outputFiles(request);
        if (result.failure().isPresent() || exit != 0) {
            boolean timedOut = result.failure()
                    .map(failure -> failure.code() == SandboxFailureCode.PROCESS_TIMEOUT)
                    .orElse(false);
            return new ExecutionResult(false, exit, stdout, stderr, duration, outputs, Map.of(), Map.of(),
                    timedOut ? "EXECUTION_TIMEOUT" : "EXECUTION_FAILED",
                    timedOut ? "cover-image provider command timed out"
                            : "cover-image provider command failed");
        }
        return new ExecutionResult(true, exit, stdout, stderr, duration, outputs,
                Map.of(), Map.of(), null, null);
    }

    private static List<String> outputFiles(ExecutionRequest request) {
        List<String> args = request.arguments();
        if (args.isEmpty()) {
            return List.of();
        }
        String last = args.get(args.size() - 1);
        Path path = Path.of(last);
        return Files.isRegularFile(path) ? List.of(path.toString()) : List.of();
    }

    private static ExecutionResult failed(long started, String errorCode, String errorMessage) {
        long duration = (System.nanoTime() - started) / 1_000_000L;
        return new ExecutionResult(false, -1, "", errorMessage, duration, List.of(), Map.of(), Map.of(),
                errorCode, errorMessage);
    }
}
