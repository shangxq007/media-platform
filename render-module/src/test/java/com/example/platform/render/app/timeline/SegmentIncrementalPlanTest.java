package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import static com.example.platform.render.app.timeline.RenderCacheTestSupport.testCacheReuseValidator;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SegmentIncrementalPlanTest {

    private IncrementalRenderPlanService service;

    @BeforeEach
    void setUp() {
        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        TimelineSemanticDiffService diffService = new TimelineSemanticDiffService(canonicalizer);
        RenderImpactAnalyzer impactAnalyzer = new RenderImpactAnalyzer();
        InternalTimelineAdapter adapter = TimelineTestSupport.internalTimelineAdapter(extensionsReader);
        RenderPlannerService planner = new RenderPlannerService(
                extensionsReader, new FinalComposerSelector(), new TimelineStickerReader(),
                new SegmentTimelinePlanner());
        service = new IncrementalRenderPlanService(
                diffService, impactAnalyzer, adapter, planner, new RenderArtifactRegistry(null, null),
                canonicalizer, new SegmentTimelinePlanner(), new RenderCacheUriResolver(new RenderCacheProperties()),
                new SegmentPlanFilter(),
                testCacheReuseValidator());
    }

    @Test
    void segmentPolicyAddsSegmentRenderTasks() throws Exception {
        String json = Files.readString(samplePath());
        IncrementalRenderPlan plan = service.generate(json, json, "default_1080p", "PRO", "mp4", null, null);
        assertTrue(plan.pipelinePlan().tasks().stream()
                .anyMatch(t -> t.type() == PipelineTaskType.SEGMENT_RENDER));
    }

    private static Path samplePath() throws Exception {
        Path path = Path.of("../../docs/media-rendering/examples/timeline-v1-full-sample.json");
        if (!Files.exists(path)) {
            path = Path.of("docs/media-rendering/examples/timeline-v1-full-sample.json");
        }
        return path;
    }
}
