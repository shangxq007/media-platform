package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * FFmpeg tool implementation.
 * 
 * <p>Handles clip and audio processing.
 */
@Component
public class FFmpegTool implements ToolRouter.RenderTool {

    private static final Logger log = LoggerFactory.getLogger(FFmpegTool.class);

    private final String ffmpegPath;

    public FFmpegTool() {
        this.ffmpegPath = System.getenv().getOrDefault("FFMPEG_PATH", "/usr/bin/ffmpeg");
    }

    @Override
    public ToolRouter.ToolResult execute(String nodeId, String nodeType, Map<String, Object> params, Map<String, String> inputs) {
        Instant startTime = Instant.now();
        log.info("FFmpeg executing node {} (type={})", nodeId, nodeType);

        try {
            // Create output directory
            Path outputDir = Path.of("/tmp/renderplan-output", nodeId);
            Files.createDirectories(outputDir);

            // Get input URI
            String inputUri = inputs.values().stream().findFirst().orElse(null);

            // Build FFmpeg command based on node type
            Path outputFile = outputDir.resolve("output.mp4");

            // For now, create a placeholder output
            // In production, this would execute actual FFmpeg commands
            Files.createFile(outputFile);

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            log.info("FFmpeg completed node {} in {}ms", nodeId, durationMs);

            return ToolRouter.ToolResult.success(outputFile.toUri().toString(), durationMs);

        } catch (IOException e) {
            log.error("FFmpeg failed for node {}: {}", nodeId, e.getMessage());
            return ToolRouter.ToolResult.failure(e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "ffmpeg";
    }

    @Override
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
