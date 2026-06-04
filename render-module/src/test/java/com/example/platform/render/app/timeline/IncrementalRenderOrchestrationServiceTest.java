package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.RenderPlannerService;
import static com.example.platform.render.app.timeline.RenderCacheTestSupport.testCacheReuseValidator;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncrementalRenderOrchestrationServiceTest {

    @Mock
    private BaseJobTimelineLoader baseJobTimelineLoader;

    private IncrementalRenderOrchestrationService orchestration;
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
        IncrementalRenderPlanService planService = new IncrementalRenderPlanService(
                diffService, impactAnalyzer, adapter, planner, new RenderArtifactRegistry(null, null), canonicalizer,
                new SegmentTimelinePlanner(), new RenderCacheUriResolver(new RenderCacheProperties()),
                new SegmentPlanFilter(),
                testCacheReuseValidator());
        TimelineSpecResolver resolver = new TimelineSpecResolver(adapter, new com.example.platform.render.domain.timeline.TimelineScriptParser());
        orchestration = new IncrementalRenderOrchestrationService(planService, resolver, baseJobTimelineLoader);
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        sampleJson = Files.readString(path);
    }

    @Test
    void resolvesIncrementalPlanWhenBaseJobHasV1Timeline() {
        when(baseJobTimelineLoader.loadInternalTimelineJson(eq("rj_base"), any()))
                .thenReturn(Optional.of(sampleJson));
        String patched = sampleJson.replace("\"segmentDurationSec\": 4", "\"segmentDurationSec\": 6");
        var spec = new TimelineSpecResolver(
                TimelineTestSupport.internalTimelineAdapter(),
                new com.example.platform.render.domain.timeline.TimelineScriptParser())
                .resolve(patched)
                .orElseThrow();

        var execution = orchestration.tryResolve(
                patched, "rj_base", "ten_demo", spec, "default_1080p", "PRO", "dash");

        assertTrue(execution.isPresent());
        assertEquals("INCREMENTAL", execution.get().incrementalPlan().mode());
        assertTrue(execution.get().plan().tasks().stream()
                .anyMatch(t -> t.parameters() != null
                        && "reuse".equals(t.parameters().get("incrementalMode"))));
    }

    @Test
    void skipsWhenBaseJobMissing() {
        when(baseJobTimelineLoader.loadInternalTimelineJson(any(), any())).thenReturn(Optional.empty());
        var spec = new TimelineSpecResolver(
                TimelineTestSupport.internalTimelineAdapter(),
                new com.example.platform.render.domain.timeline.TimelineScriptParser())
                .resolve(sampleJson)
                .orElseThrow();
        assertTrue(orchestration.tryResolve(
                sampleJson, "rj_missing", "ten_demo", spec, "default_1080p", "PRO", "dash").isEmpty());
    }
}
