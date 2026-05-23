package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.domain.timeline.internal.ReusableArtifact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenderArtifactRegistryPipelineStagesTest {

    @Test
    void loadsPipelineStageArtifactsFromBaseJobExecutionState() {
        PipelinePlanPersistenceService persistence = mock(PipelinePlanPersistenceService.class);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pipelineStageArtifacts", Map.of(
                "effects", "localFs://base/effects.mp4",
                "final_compose", "localFs://base/compose.mp4"));
        when(persistence.loadExecutionState("rj_base")).thenReturn(Optional.of(state));

        RenderArtifactRegistry registry = new RenderArtifactRegistry(persistence, null);
        List<ReusableArtifact> artifacts = registry.resolve("rj_base", List.of());

        assertEquals(2, artifacts.size());
        assertTrue(artifacts.stream().anyMatch(a -> "effects".equals(a.taskId())
                && a.uri().contains("effects.mp4")));
        assertTrue(artifacts.stream().anyMatch(a -> "final_compose".equals(a.taskId())));
    }
}
