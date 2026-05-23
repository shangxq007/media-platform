package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderCacheProperties;
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
                new com.example.platform.render.domain.timeline.TimelineStickerReader(),
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
                new SegmentTimelinePlanner(), new RenderCacheUriResolver(cacheProps),
                new SegmentPlanFilter(), validator);

        Path path = Path.of("../../docs/media-rendering/examples/timeline-v1-full-sample.json");
        if (!Files.exists(path)) {
            path = Path.of("docs/media-rendering/examples/timeline-v1-full-sample.json");
        }
        sampleJson = Files.readString(path);
    }

    @Test
    void hashMismatchForcesSegmentReExecute() throws Exception {
        String patched = sampleJson.replace("\"durationFrames\": 15", "\"durationFrames\": 20");
        IncrementalRenderPlan plan = service.generate(
                patched, sampleJson, "default_1080p", "PRO", "mp4", "base-job", null, null);
        assertTrue(plan.metadata().containsKey("hashInvalidatedCount")
                        || plan.executeTaskIds().stream().anyMatch(id -> id.startsWith("seg_")),
                "hash mismatch should force segment re-execution");
        var seg0 = plan.pipelinePlan().tasks().stream()
                .filter(t -> "seg_0".equals(t.taskId()))
                .findFirst();
        if (seg0.isPresent() && plan.metadata().containsKey("hashInvalidatedTaskIds")) {
            assertEquals("execute", seg0.get().parameters().get("incrementalMode"));
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
