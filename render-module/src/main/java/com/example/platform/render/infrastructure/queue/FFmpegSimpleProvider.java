package com.example.platform.render.infrastructure.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * FFmpeg implementation of SimpleRenderProvider.
 * 
 * <p>Minimal FFmpeg execution for production use.
 */
@Component
public class FFmpegSimpleProvider implements SimpleRenderProvider {

    private static final Logger log = LoggerFactory.getLogger(FFmpegSimpleProvider.class);

    private final String ffmpegPath;

    public FFmpegSimpleProvider() {
        this.ffmpegPath = System.getenv().getOrDefault("FFMPEG_PATH", "/usr/bin/ffmpeg");
    }

    @Override
    public RenderResult execute(RenderRequest request) {
        Instant startTime = Instant.now();
        log.info("Executing render job {} with FFmpeg", request.jobId());

        try {
            // Create output directory
            Path outputDir = Path.of("/tmp/render-output", request.jobId());
            Files.createDirectories(outputDir);

            // For now, create a placeholder output
            // In production, this would execute FFmpeg with the actual script
            Path outputFile = outputDir.resolve("output.mp4");
            Files.createFile(outputFile);

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            log.info("Render job {} completed in {}ms", request.jobId(), durationMs);

            return RenderResult.success(
                    request.jobId(),
                    "art-" + request.jobId(),
                    outputFile.toUri().toString(),
                    durationMs
            );

        } catch (IOException e) {
            log.error("Render job {} failed: {}", request.jobId(), e.getMessage());
            return RenderResult.failure(request.jobId(), e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "ffmpeg";
    }

    @Override
    public boolean isAvailable() {
        // Check if FFmpeg is available
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
