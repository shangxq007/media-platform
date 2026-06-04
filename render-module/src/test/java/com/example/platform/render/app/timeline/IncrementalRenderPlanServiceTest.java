package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.planner.RenderPlannerService;
import static com.example.platform.render.app.timeline.RenderCacheTestSupport.testCacheReuseValidator;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncrementalRenderPlanServiceTest {

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
        RenderArtifactRegistry registry = new RenderArtifactRegistry(null, null);
        service = new IncrementalRenderPlanService(
                diffService, impactAnalyzer, adapter, planner, registry, canonicalizer,
                new SegmentTimelinePlanner(), new RenderCacheUriResolver(new RenderCacheProperties()),
                new SegmentPlanFilter(),
                testCacheReuseValidator());
    }

    @Test
    void packagingOnlyChangeReusesMostTasks() throws Exception {
        String base = loadSample();
        String patched = base.replace("\"segmentDurationSec\": 4", "\"segmentDurationSec\": 6");
        IncrementalRenderPlan plan = service.generate(
                patched, base, "default_1080p", "PRO", "dash", null, null);
        assertEquals(IncrementalRenderPlan.MODE_INCREMENTAL, plan.mode());
        assertFalse(plan.fullReRenderRequired());
        assertTrue(plan.reuseTaskIds().contains("final_compose")
                || plan.reuseTaskIds().contains("transcode"));
        assertTrue(plan.executeTaskIds().contains("packaging")
                || plan.pipelinePlan().tasks().stream()
                        .filter(t -> t.type() == PipelineTaskType.PACKAGING)
                        .anyMatch(t -> "execute".equals(t.parameters().get("incrementalMode"))));
    }

    @Test
    void v1SampleProducesPlanWithExternalTasks() throws Exception {
        String json = loadSample();
        IncrementalRenderPlan plan = service.generate(
                json, json, "default_1080p", "PRO", "dash", null, null);
        assertNotNull(plan.pipelinePlan());
        assertTrue(plan.pipelinePlan().tasks().stream()
                .anyMatch(t -> t.type() == PipelineTaskType.EXTERNAL_RENDER));
    }

    private String loadSample() throws Exception {
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        return Files.readString(path);
    }
}
