package com.example.platform.render.infrastructure.renderplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderToolsFailClosedTest {

    @Test
    void remotionIsUnavailableAndCreatesNoPlaceholderArtifact() {
        RemotionTool tool = new RemotionTool();
        String nodeId = "remotion-fail-closed-" + UUID.randomUUID();
        Path formerPlaceholder = Path.of("/tmp/renderplan-output", nodeId, "output.mp4");

        var result = tool.execute(nodeId, "SCENE", Map.of(), Map.of());

        assertFalse(tool.isAvailable());
        assertFalse(result.success());
        assertNull(result.outputUri());
        assertEquals(RemotionTool.UNAVAILABLE_REASON, result.error());
        assertEquals(0, result.durationMs());
        assertFalse(Files.exists(formerPlaceholder));
    }

    @Test
    void mltIsUnavailableAndCreatesNoPlaceholderArtifact() {
        MLTTool tool = new MLTTool();
        String nodeId = "mlt-fail-closed-" + UUID.randomUUID();
        Path formerPlaceholder = Path.of("/tmp/renderplan-output", nodeId, "output.mp4");

        var result = tool.execute(nodeId, "TRANSITION", Map.of(), Map.of());

        assertFalse(tool.isAvailable());
        assertFalse(result.success());
        assertNull(result.outputUri());
        assertEquals(MLTTool.UNAVAILABLE_REASON, result.error());
        assertEquals(0, result.durationMs());
        assertFalse(Files.exists(formerPlaceholder));
    }
}
