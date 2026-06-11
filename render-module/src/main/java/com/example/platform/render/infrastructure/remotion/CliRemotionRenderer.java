package com.example.platform.render.infrastructure.remotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(request.workingDir().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    logs.add(line);
                    log.debug("[remotion stdout] {}", line);
                }
                while ((line = errReader.readLine()) != null) {
                    errors.add(line);
                    log.warn("[remotion stderr] {}", line);
                }
            }

            int exitCode = process.waitFor();
            long durationMs = System.currentTimeMillis() - startTime;

            String outputUri = request.outputPath().toString();
            boolean success = exitCode == 0 && Files.exists(request.outputPath());

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
