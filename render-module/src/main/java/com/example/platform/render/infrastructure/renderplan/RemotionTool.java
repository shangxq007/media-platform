package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * Remotion tool implementation.
 * 
 * <p>Handles scene processing.
 */
@Component
public class RemotionTool implements ToolRouter.RenderTool {

    private static final Logger log = LoggerFactory.getLogger(RemotionTool.class);

    @Override
    public ToolRouter.ToolResult execute(String nodeId, String nodeType, Map<String, Object> params, Map<String, String> inputs) {
        Instant startTime = Instant.now();
        log.info("Remotion executing node {} (type={})", nodeId, nodeType);

        try {
            // Create output path
            Path outputFile = Path.of("/tmp/renderplan-output", nodeId, "output.mp4");

            // For now, create a placeholder output
            // In production, this would execute actual Remotion commands
            java.nio.file.Files.createDirectories(outputFile.getParent());
            java.nio.file.Files.createFile(outputFile);

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            log.info("Remotion completed node {} in {}ms", nodeId, durationMs);

            return ToolRouter.ToolResult.success(outputFile.toUri().toString(), durationMs);

        } catch (Exception e) {
            log.error("Remotion failed for node {}: {}", nodeId, e.getMessage());
            return ToolRouter.ToolResult.failure(e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "remotion";
    }

    @Override
    public boolean isAvailable() {
        // Remotion is always available (stub implementation)
        return true;
    }
}
