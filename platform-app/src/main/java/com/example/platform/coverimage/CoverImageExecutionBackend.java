package com.example.platform.coverimage;

import com.example.platform.sandbox.execution.ExecutionBackend;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.ExecutionResult;
import com.example.platform.sandbox.execution.TaskCapability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sandbox execution backend for the cover-image capability: the registered provider's command runs
 * inside a bubblewrap profile with the pinned FFmpeg binary. No shell is involved and no other
 * capability is accepted.
 */
@Component
public final class CoverImageExecutionBackend implements ExecutionBackend {

    private final String bwrap;
    private final String ffmpeg;

    public CoverImageExecutionBackend(
            @Value("${platform.cover-image.sandbox.bwrap:/usr/bin/bwrap}") String bwrap,
            @Value("${platform.cover-image.sandbox.ffmpeg:/usr/bin/ffmpeg}") String ffmpeg) {
        this.bwrap = bwrap;
        this.ffmpeg = ffmpeg;
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
        List<String> command = new ArrayList<>(List.of(
                bwrap,
                "--ro-bind", "/", "/",
                "--dev", "/dev",
                "--proc", "/proc",
                "--tmpfs", "/tmp",
                "--unshare-all",
                "--die-with-parent",
                ffmpeg));
        command.addAll(request.arguments());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            byte[] out = process.getInputStream().readAllBytes();
            byte[] err = process.getErrorStream().readAllBytes();
            boolean finished = process.waitFor(request.timeoutSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            long duration = (System.nanoTime() - started) / 1_000_000L;
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(false, -1, new String(out, StandardCharsets.UTF_8),
                        new String(err, StandardCharsets.UTF_8), duration, List.of(), Map.of(), Map.of(),
                        "EXECUTION_TIMEOUT", "cover-image provider command timed out");
            }
            int exit = process.exitValue();
            List<String> outputs = outputFiles(request);
            if (exit != 0) {
                return new ExecutionResult(false, exit, new String(out, StandardCharsets.UTF_8),
                        new String(err, StandardCharsets.UTF_8), duration, outputs, Map.of(), Map.of(),
                        "EXECUTION_FAILED", "cover-image provider command failed");
            }
            return new ExecutionResult(true, exit, new String(out, StandardCharsets.UTF_8),
                    new String(err, StandardCharsets.UTF_8), duration, outputs, Map.of(), Map.of(), null, null);
        } catch (IOException failure) {
            throw new IllegalStateException("cover-image sandbox execution failed", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cover-image sandbox execution interrupted", interrupted);
        }
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
}
