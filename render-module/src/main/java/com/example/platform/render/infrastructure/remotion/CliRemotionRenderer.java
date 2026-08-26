package com.example.platform.render.infrastructure.remotion;

import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CliRemotionRenderer implements RemotionRenderer {
    private static final Logger log = LoggerFactory.getLogger(CliRemotionRenderer.class);

    private Path remotionBinary = Path.of("npx");

    public CliRemotionRenderer remotionBinary(Path binary) {
        this.remotionBinary = binary;
        return this;
    }

    @Override
    public RemotionRenderResult render(RemotionRenderRequest request) {
        RemotionRenderCommandBuilder builder = new RemotionRenderCommandBuilder()
                .remotionBinary(remotionBinary)
                .compositionId(request.compositionId())
                .workingDir(request.workingDir())
                .outputPath(request.outputPath())
                .inputProps(request.inputProps())
                .format(request.format())
                .width(request.width())
                .height(request.height())
                .fps(request.fps())
                .concurrency(request.concurrency())
                .overwrite(request.overwrite());

        List<String> command = builder.build();
        List<String> logs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            var process = LocalSandboxProcess.execute(command,
                    request.workingDir().toAbsolutePath().normalize(),
                    request.workingDir().toAbsolutePath().normalize(), java.util.Set.of(),
                    java.util.Map.of("PATH", "/usr/bin:/bin", "LANG", "C"),
                    java.time.Duration.ofMinutes(30), 4L << 20, SandboxCancellation.never());
            logs.addAll(process.stdout().utf8().lines().toList());
            errors.addAll(process.stderr().utf8().lines().toList());
            process.failure().ifPresent(failure -> errors.add(failure.code() + ": " + failure.message()));
            int exitCode = process.exitCode().orElse(-1);
            long durationMs = System.currentTimeMillis() - startTime;

            String outputUri = request.outputPath().toString();
            boolean success = process.failure().isEmpty() && exitCode == 0 && Files.exists(request.outputPath());

            return new RemotionRenderResult(
                    request.compositionId(),
                    request.compositionId(),
                    request.outputPath(),
                    outputUri,
                    durationMs,
                    request.width(),
                    request.height(),
                    request.fps(),
                    request.format(),
                    success,
                    logs,
                    errors,
                    exitCode
            );
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            errors.add("Remotion render failed: " + e.getMessage());
            log.error("Remotion render failed", e);
            return new RemotionRenderResult(
                    request.compositionId(),
                    request.compositionId(),
                    request.outputPath(),
                    null,
                    durationMs,
                    request.width(),
                    request.height(),
                    request.fps(),
                    request.format(),
                    false,
                    logs,
                    errors,
                    -1
            );
        }
    }
}
