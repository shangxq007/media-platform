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

class SegmentFinalComposeIncrementalTest {

    private IncrementalRenderPlanService service;
    private String sampleJson;

    @BeforeEach
    void setUp() throws Exception {
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
        Path path = Path.of("../../docs/media-rendering/examples/timeline-v1-full-sample.json");
        if (!Files.exists(path)) {
            path = Path.of("docs/media-rendering/examples/timeline-v1-full-sample.json");
        }
        sampleJson = Files.readString(path);
    }

    @Test
    void subtitleCueChangeReexecutesFinalComposeWithSegmentPolicy() throws Exception {
        String base = sampleJson;
        String patched = base.replace("新品发布", "新品发布（修订）");
        IncrementalRenderPlan plan = service.generate(
                patched, base, "default_1080p", "PRO", "mp4", null, null);
        assertEquals(IncrementalRenderPlan.MODE_INCREMENTAL, plan.mode());
        var finalCompose = plan.pipelinePlan().tasks().stream()
                .filter(t -> t.type() == PipelineTaskType.FINAL_COMPOSE)
                .findFirst()
                .orElseThrow();
        assertEquals("execute", finalCompose.parameters().get("incrementalMode"),
                "subtitle change must re-stitch segments when segmentPolicy is enabled");
        assertTrue(plan.executeTaskIds().contains("final_compose"));
    }

    @Test
    void clipEffectChangeReexecutesFinalCompose() throws Exception {
        String base = sampleJson;
        String patched = base.replace("\"durationFrames\": 15", "\"durationFrames\": 20");
        IncrementalRenderPlan plan = service.generate(
                patched, base, "default_1080p", "PRO", "mp4", null, null);
        assertTrue(plan.pipelinePlan().tasks().stream()
                .anyMatch(t -> t.type() == PipelineTaskType.SEGMENT_RENDER
                        && "execute".equals(t.parameters().get("incrementalMode"))));
        assertEquals("execute", plan.pipelinePlan().tasks().stream()
                .filter(t -> "final_compose".equals(t.taskId()))
                .findFirst().orElseThrow().parameters().get("incrementalMode"));
    }
}
