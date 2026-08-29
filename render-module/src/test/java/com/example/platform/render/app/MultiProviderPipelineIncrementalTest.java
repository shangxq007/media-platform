package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.timeline.SegmentStitchComposeService;
import com.example.platform.render.app.timeline.SegmentTimelinePlanner;
import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineStickerReader;
import com.example.platform.render.infrastructure.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiProviderPipelineIncrementalTest {

    private static final String RESERVED_UNBOUND_BACKEND = "provider";

    @Mock
    private RenderProviderRegistry providerRegistry;
    @Mock
    private RenderProviderRouter providerRouter;
    @Mock
    private ExportPolicyService exportPolicy;
    @Mock
    private EffectMappingService effectMapping;

    private MultiProviderPipelineService pipelineService;
    private RenderPlannerService planner;

    @BeforeEach
    void setUp() {
        planner = new RenderPlannerService(
                new TimelineExtensionsReader(), new FinalComposerSelector(), new TimelineStickerReader(),
                new SegmentTimelinePlanner());
        TimelineExecutorService timelineExecutor = new TimelineExecutorService(planner);
        pipelineService = new MultiProviderPipelineService(
                providerRouter, providerRegistry, exportPolicy, effectMapping,
                timelineExecutor,
                new SegmentStitchComposeService(
                        mock(com.example.platform.extension.app.ProcessToolRunner.class),
                        new com.example.platform.render.domain.interchange.TimelineScriptParser(),
                        new com.example.platform.render.infrastructure.mlt.MltProjectXmlBuilder(),
                        new com.example.platform.render.infrastructure.mlt.MLTCommandFactory(),
                        Optional.empty(),
                        Optional.empty()),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void reuseCandidateNeverSkipsProviderWithoutArtifactValidation() {
        RenderProvider provider = mock(RenderProvider.class);
        when(providerRegistry.getProvider("provider-a")).thenReturn(Optional.of(provider));
        when(provider.render(anyString(), anyString(), anyString()))
                .thenReturn(new RenderProvider.RenderResult(
                        "art-tc", "localFs://artifacts/job-inc/transcode-output.mp4", 10L, "mp4", "1920x1080"));

        TimelineSpec timeline = TimelineSpec.create("tl-inc", "Inc", TimelineOutputSpec.mp4_1080p30());
        Map<String, String> effectParams = new LinkedHashMap<>();
        effectParams.put("incrementalMode", "reuse-candidate");

        PipelineExecutionPlan plan = new PipelineExecutionPlan(
                "pep-inc",
                timeline.id(),
                com.example.platform.render.domain.planning.FinalComposerHint.TYPED_PROVIDER_PLUGIN,
                List.of(
                        PipelineTask.of("effects", "effects", PipelineTaskType.EFFECTS, "provider-a",
                                List.of(), effectParams),
                        PipelineTask.of("transcode", "transcode", PipelineTaskType.TRANSCODE, "provider-a",
                                List.of("effects"), Map.of())),
                Map.of("mode", "INCREMENTAL"));

        MultiProviderPipelineService.PipelineResult result = pipelineService.executePipeline(
                "job-inc", timeline, "default_1080p", "PRO", "mp4", plan);

        assertTrue(result.success());
        verify(provider, times(2)).render(anyString(), anyString(), anyString());
        assertEquals("localFs://artifacts/job-inc/transcode-output.mp4",
                result.stages().get(0).storageUri());
    }

    @Test
    void reservedCollapsedProviderIdentityFailsClosedBeforeRegistryLookup() {
        TimelineSpec timeline = TimelineSpec.create(
                "tl-unbound", "Unbound", TimelineOutputSpec.mp4_1080p30());
        PipelineExecutionPlan plan = new PipelineExecutionPlan(
                "pep-unbound",
                timeline.id(),
                com.example.platform.render.domain.planning.FinalComposerHint.TYPED_PROVIDER_PLUGIN,
                List.of(PipelineTask.of(
                        "transcode", "transcode", PipelineTaskType.TRANSCODE,
                        RESERVED_UNBOUND_BACKEND, List.of(), Map.of())),
                Map.of());

        MultiProviderPipelineService.PipelineResult result = pipelineService.executePipeline(
                "job-unbound", timeline, "default_1080p", "PRO", "mp4", plan);

        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorCode());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void unresolvedProviderIdentityFailsClosedBeforeRegistryLookup() {
        TimelineSpec timeline = TimelineSpec.create(
                "tl-unresolved", "Unresolved", TimelineOutputSpec.mp4_1080p30());
        PipelineExecutionPlan plan = new PipelineExecutionPlan(
                "pep-unresolved",
                timeline.id(),
                com.example.platform.render.domain.planning.FinalComposerHint.TYPED_PROVIDER_PLUGIN,
                List.of(PipelineTask.of(
                        "transcode", "transcode", PipelineTaskType.TRANSCODE,
                        null, List.of(), Map.of())),
                Map.of());

        MultiProviderPipelineService.PipelineResult result = pipelineService.executePipeline(
                "job-unresolved", timeline, "default_1080p", "PRO", "mp4", plan);

        assertFalse(result.success());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorCode());
        assertEquals("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED", result.errorMessage());
        verifyNoInteractions(providerRegistry);
    }
}
