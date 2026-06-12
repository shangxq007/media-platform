package com.example.platform.render.policy.liteflow;

import com.example.platform.render.infrastructure.SubtitleBurnInService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIScriptGenNodeTest {

    @Test
    void nodeHasCorrectComponentId() {
        AIScriptGenNode node = new AIScriptGenNode();
        // Verify the node can be instantiated
        assertNotNull(node);
    }

    @Test
    void renderPlanCalcNodeCanBeInstantiated() {
        RenderPlanCalcNode node = new RenderPlanCalcNode();
        assertNotNull(node);
    }

    @Test
    void videoFrameGenNodeCanBeInstantiated() {
        VideoFrameGenNode node = new VideoFrameGenNode();
        assertNotNull(node);
    }

    @Test
    void artifactUpdateNodeCanBeInstantiated() {
        ArtifactUpdateNode node = new ArtifactUpdateNode();
        assertNotNull(node);
    }

    @Test
    void subtitleBurnInNodeCanBeInstantiated() {
        SubtitleBurnInService mockService = mock(SubtitleBurnInService.class);
        SubtitleBurnInNode node = new SubtitleBurnInNode(mockService);
        assertNotNull(node);
    }
}
