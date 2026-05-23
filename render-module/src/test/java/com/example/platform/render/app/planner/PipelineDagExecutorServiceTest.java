package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.MultiProviderPipelineService;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PipelineDagExecutorServiceTest {

    @Test
    void executesExternalThenPipeline() {
        RenderPlannerService planner = new RenderPlannerService(
                new TimelineExtensionsReader(), new FinalComposerSelector(), new TimelineStickerReader(),
                new com.example.platform.render.app.timeline.SegmentTimelinePlanner());
        RenderProviderRegistry registry = mock(RenderProviderRegistry.class);
        MultiProviderPipelineService pipeline = mock(MultiProviderPipelineService.class);
        RenderProvider blender = mock(RenderProvider.class);

        when(registry.getProvider("blender")).thenReturn(Optional.of(blender));
        when(blender.render(anyString(), anyString(), anyString()))
                .thenReturn(new RenderProvider.RenderResult("art-xr", "localFs://xr", 5L, "mp4", "1920x1080"));
        when(pipeline.executePipeline(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(MultiProviderPipelineService.PipelineResult.success(
                        "job-1", java.util.List.of(), 100L));
        when(pipeline.executePipeline(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(MultiProviderPipelineService.PipelineResult.success(
                        "job-1", java.util.List.of(), 100L));

        PipelineDagExecutorService executor = new PipelineDagExecutorService(
                planner, registry, pipeline, new TimelineExtensionsReader(),
                mock(PipelinePlanPersistenceService.class),
                new com.example.platform.render.app.timeline.SegmentTimelinePlanner(),
                new com.example.platform.render.app.timeline.SegmentCachePublisher(
                        new com.example.platform.render.app.timeline.RenderCacheUriResolver(
                                new com.example.platform.render.infrastructure.RenderCacheProperties()),
                        new com.example.platform.render.infrastructure.RenderCacheProperties()),
                java.util.Optional.empty(),
                new com.example.platform.render.app.timeline.MezzanineCachePublisher());
        ReflectionTestUtils.setField(executor, "dagEnabled", true);

        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        Map<String, String> meta = new java.util.LinkedHashMap<>(spec.metadata());
        meta.put("platform.externalRenderNodes", """
                [{"id":"xr1","backend":"blender","timelineStart":0,"duration":5}]
                """);
        spec = new TimelineSpec(spec.id(), spec.name(), spec.description(), spec.tracks(),
                spec.textOverlays(), spec.outputSpec(), spec.totalDuration(), meta);

        assertTrue(executor.shouldExecuteAsDag(spec, "default_1080p"));
        PipelineDagExecutorService.DagExecutionResult result =
                executor.execute("job-1", spec, "default_1080p", "PRO", "mp4");
        assertTrue(result.success());
        verify(blender).render(contains("xr1"), anyString(), eq("default_1080p"));
        verify(pipeline).executePipeline(eq("job-1"), any(), eq("default_1080p"), eq("PRO"), eq("mp4"), any());
    }
}
