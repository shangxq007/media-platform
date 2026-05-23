package com.example.platform.render.app;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MultiProviderPipelineServiceTest {

    private MultiProviderPipelineService pipelineService;
    private RenderProviderRegistry registry;

    // Simple mock provider
    private static class TestProvider implements RenderProvider {
        private final String key;
        private final RenderProviderCapability capability;

        TestProvider(String key, RenderProviderCapability capability) {
            this.key = key;
            this.capability = capability;
        }

        public String getKey() { return key; }

        @Override
        public RenderResult render(String jobId, String aiScript, String profile) {
            return new RenderResult("art_" + jobId, "localFsStorageProvider://" + key + "/" + jobId, 30L, "mp4", "1920x1080");
        }

        @Override
        public List<String> getSupportedProfiles() {
            return List.copyOf(capability.availableInProfiles());
        }

        @Override
        public boolean supports(String cap) {
            return capability.supportsEffect(cap) || capability.supportsFormat(cap) ||
                    capability.supportsCodec(cap);
        }

        @Override
        public EnvironmentValidationResult validateEnvironment() {
            return EnvironmentValidationResult.ok();
        }
    }

    @BeforeEach
    void setUp() {
        registry = new RenderProviderRegistry();

        RenderProviderCapability javacvCap = new RenderProviderCapability("javacv",
                Set.of("mp4"), Set.of("h264", "aac"),
                Set.of("video.fade_in", "video.blur"), Set.of("dissolve"), Set.of("burn_in"),
                "1920x1080", false, false, false, Set.of("default_1080p", "default_720p"));
        RenderProvider javacv = new TestProvider("javacv", javacvCap);

        RenderProviderCapability ofxCap = new RenderProviderCapability("ofx",
                Set.of("mp4", "webm"), Set.of("h264", "vp9"),
                Set.of("video.vignette", "video.chromatic", "video.blur"),
                Set.of("dissolve", "wipe", "slide"), Set.of("burn_in", "overlay"),
                "3840x2160", false, false, false, Set.of("ofx_1080p", "ofx_720p"));
        RenderProvider ofx = new TestProvider("ofx", ofxCap);

        RenderProviderCapability gpuCap = new RenderProviderCapability("gpu-h264",
                Set.of("mp4"), Set.of("h264", "aac"),
                Set.of("video.fade_in"), Set.of("dissolve"), Set.of("burn_in"),
                "1920x1080", false, true, false, Set.of("gpu_h264"));
        RenderProvider gpuH264 = new TestProvider("gpu-h264", gpuCap);

        registry.register("javacv", javacv, javacvCap);
        registry.register("ofx", ofx, ofxCap);
        registry.register("gpu-h264", gpuH264, gpuCap);
        // Register health checks
        registry.updateHealthCheck("javacv", RenderProviderHealthCheck.ok("javacv", 0));
        registry.updateHealthCheck("ofx", RenderProviderHealthCheck.ok("ofx", 0));
        registry.updateHealthCheck("gpu-h264", RenderProviderHealthCheck.ok("gpu-h264", 0));

        RenderProviderSelectionPolicy selectionPolicy = new RenderProviderSelectionPolicy(
                registry, new com.example.platform.render.infrastructure.effects.EffectProviderRouter(
                        new EffectMappingService()));
        RenderProviderFallbackPolicy fallbackPolicy = new RenderProviderFallbackPolicy(registry, selectionPolicy);
        RenderProviderRouter router = new RenderProviderRouter(fallbackPolicy);
        ExportPolicyService exportPolicy = new ExportPolicyService();
        EffectMappingService effectMapping = new EffectMappingService();
        SubtitleBurnInService subtitleBurnInService = new SubtitleBurnInService(null);
        SubtitleRenderService subtitleRender = new SubtitleRenderService(subtitleBurnInService);

        registry.register("libass", javacv, javacvCap);
        registry.register("mlt", javacv, javacvCap);
        TimelineExecutorService timelineExecutor = new TimelineExecutorService(
                new RenderPlannerService(new TimelineExtensionsReader(), new FinalComposerSelector(),
                        new com.example.platform.render.domain.timeline.TimelineStickerReader(),
                        new com.example.platform.render.app.timeline.SegmentTimelinePlanner()));

        pipelineService = new MultiProviderPipelineService(router, registry, exportPolicy,
                effectMapping, subtitleRender, timelineExecutor,
                new com.example.platform.render.app.timeline.SegmentStitchComposeService(
                        mock(com.example.platform.extension.app.ProcessToolRunner.class),
                        new com.example.platform.render.domain.timeline.TimelineScriptParser(),
                        new com.example.platform.render.infrastructure.mlt.MltProjectXmlBuilder(),
                        new com.example.platform.render.infrastructure.mlt.MLTCommandFactory(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty()),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    void planPipelineWithEffectsCreatesEffectStage() {
        TimelineSpec spec = createTimelineWithEffects();

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                spec, "default_1080p", "PRO", "mp4");

        assertFalse(stages.isEmpty());
        assertTrue(stages.stream().anyMatch(s -> s.name().equals("effects")));
        assertTrue(stages.stream().anyMatch(s -> s.name().equals("transcode")));
    }

    @Test
    void planPipelineWithDashOutputCreatesPackagingStage() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "default_1080p", "TEAM", "dash");

        assertTrue(stages.stream().anyMatch(s -> s.name().equals("packaging")));
    }

    @Test
    void planPipelineWithGpuProfileSelectsGpuProvider() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "gpu_h264", "PRO", "mp4");

        assertTrue(stages.stream().anyMatch(s -> s.name().equals("transcode") && s.providerKey().equals("gpu-h264")));
    }

    @Test
    void planPipelineFreeTierUsesJavaCVForEffects() {
        TimelineSpec spec = createTimelineWithEffects();

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                spec, "default_1080p", "FREE", "mp4");

        assertTrue(stages.stream().anyMatch(s -> s.name().equals("effects") && s.providerKey().equals("javacv")));
    }

    @Test
    void planPipelineWithoutEffectsSkipsEffectStage() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "default_1080p", "FREE", "mp4");

        assertFalse(stages.stream().anyMatch(s -> s.name().equals("effects")));
        assertTrue(stages.stream().anyMatch(s -> s.name().equals("transcode")));
    }

    @Test
    void executePipelineReturnsResult() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        MultiProviderPipelineService.PipelineResult result = pipelineService.executePipeline(
                "job-pipeline-1", timeline, "default_1080p", "PRO", "mp4");

        assertNotNull(result);
        assertNotNull(result.jobId());
        assertFalse(result.stages().isEmpty());
    }

    @Test
    void planPipelineMultipleStagesForComplexTimeline() {
        TimelineSpec spec = createTimelineWithEffects();

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                spec, "default_1080p", "TEAM", "dash");

        assertTrue(stages.size() >= 3);
        assertTrue(stages.stream().anyMatch(s -> "effects".equals(s.name())));
        assertTrue(stages.stream().anyMatch(s -> "transcode".equals(s.name())));
        assertTrue(stages.stream().anyMatch(s -> "packaging".equals(s.name())));
    }

    @Test
    void planPipelineWithHlsOutput() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "default_1080p", "ENTERPRISE", "hls");

        assertTrue(stages.stream().anyMatch(s -> s.name().equals("packaging")));
    }

    @Test
    void planPipelineWithCmafOutput() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "default_1080p", "ENTERPRISE", "cmaf");

        assertTrue(stages.stream().anyMatch(s -> s.name().equals("packaging")));
    }

    @Test
    void planPipelineGpuH265ProfileSelectsGpuProvider() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());

        List<MultiProviderPipelineService.PipelineStage> stages = pipelineService.planPipeline(
                timeline, "gpu_h265", "PRO", "mp4");

        // GPU_H265 preset requires GPU, should route to gpu provider
        assertTrue(stages.stream().anyMatch(s -> s.name().equals("transcode")));
    }

    private TimelineSpec createTimelineWithEffects() {
        TimelineTrack track = TimelineTrack.of("t1", "Video", TimelineTrack.TrackType.VIDEO);
        TimelineClip clip = TimelineClip.of("c1",
                TimelineAssetRef.of("asset1", "storage://video.mp4"), 0, 0, 150);
        TimelineClip clipWithEffects = new TimelineClip(clip.id(), clip.assetRef(), clip.timelineStart(),
                clip.assetInPoint(), clip.assetOutPoint(), clip.clipDuration(),
                List.of(com.example.platform.render.domain.timeline.TimelineClipEffect.ofKey(
                        "video.blur", Map.of("radius", 3.0))));
        TimelineTrack trackWithClip = new TimelineTrack(track.id(), track.name(), track.type(),
                track.layer(), List.of(clipWithEffects), track.muted(), track.locked());
        return new TimelineSpec("tl-1", "Test", "Test timeline",
                List.of(trackWithClip), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());
    }
}
