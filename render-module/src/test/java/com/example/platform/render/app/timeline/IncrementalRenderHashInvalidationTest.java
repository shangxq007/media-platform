package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelineCanonicalizer;import com.example.platform.timeline.app.TimelineSemanticDiffService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.planning.IncrementalRenderPlan;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncrementalRenderHashInvalidationTest {

    @TempDir
    Path tempDir;

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
                extensionsReader, new FinalComposerSelector(),
                new com.example.platform.render.domain.legacy.TimelineStickerReader(),
                new SegmentTimelinePlanner());

        PipelinePlanPersistenceService persistence = mock(PipelinePlanPersistenceService.class);
        when(persistence.loadExecutionState(anyString())).thenReturn(Optional.of(baseExecutionState()));

        RenderCacheProperties cacheProps = new RenderCacheProperties();
        cacheProps.setContentHashEnabled(true);
        cacheProps.setInvalidateOnHashMismatch(true);

        com.example.platform.storage.domain.BlobStorage blobStorage =
                mock(com.example.platform.storage.domain.BlobStorage.class);
        TimelineScriptParser parser = new TimelineScriptParser();
        com.example.platform.shared.web.ErrorCodeRegistry registry =
                new com.example.platform.shared.web.ErrorCodeRegistry();
        registry.loadErrorCodes();
        RenderCacheArtifactFetcher fetcher = new RenderCacheArtifactFetcher(blobStorage, parser, registry);
        RenderCacheReuseValidator validator = new RenderCacheReuseValidator(cacheProps, parser, fetcher);

        service = new IncrementalRenderPlanService(
                diffService, impactAnalyzer, adapter, planner,
                new RenderArtifactRegistry(persistence, null), canonicalizer,
                new SegmentTimelinePlanner(), new SegmentPlanFilter());

        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        sampleJson = Files.readString(path);
    }

    @Test
    void legacyHashAndUriHintsRemainCandidatesAndCannotSkipExecution() throws Exception {
        String patched = sampleJson.replace("\"durationFrames\": 15", "\"durationFrames\": 20");
        IncrementalRenderPlan plan = service.generate(
                patched, sampleJson, "default_1080p", "PRO", "mp4", "base-job", null, null);
        assertFalse(plan.metadata().containsKey("hashInvalidatedCount"));
        assertTrue(plan.executeTaskIds().stream().anyMatch(id -> id.startsWith("seg_")));
        var seg0 = plan.pipelinePlan().tasks().stream()
                .filter(t -> "seg_0".equals(t.taskId()))
                .findFirst();
        if (seg0.isPresent()) {
            assertFalse(seg0.get().parameters().containsKey("skipExecution"));
            assertFalse(seg0.get().parameters().containsKey("reuseArtifactUri"));
        }
    }

    private Map<String, Object> baseExecutionState() {
        return Map.of(
                "segmentCacheIndex", Map.of(
                        "segment:tl_demo_v1_001:seg_0:r42:SEGMENT", Map.of(
                                "segmentId", "seg_0",
                                "uri", "localFsStorageProvider://artifacts/base/seg_0/output.mp4",
                                "remoteUri", "localFsStorageProvider://artifacts/base/seg_0/output.mp4",
                                "cacheKey", "segment:tl_demo_v1_001:seg_0:r42:SEGMENT",
                                "contentHash", "sha256:deadbeef")));
    }
}
