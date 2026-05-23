package com.example.platform.render.infrastructure.shotstack;

import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.MediaProbeService;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class ShotstackRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(ShotstackRenderProvider.class);

    private final ShotstackTimelineMapper timelineMapper;
    private final ShotstackApiClient apiClient;
    private final ShotstackRenderProviderProperties properties;
    private final TimelineScriptParser timelineScriptParser;
    private final MediaProbeService mediaProbeService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public ShotstackRenderProvider(ShotstackTimelineMapper timelineMapper,
                                   ShotstackApiClient apiClient,
                                   ShotstackRenderProviderProperties properties,
                                   TimelineScriptParser timelineScriptParser,
                                   MediaProbeService mediaProbeService) {
        this.timelineMapper = timelineMapper;
        this.apiClient = apiClient;
        this.properties = properties;
        this.timelineScriptParser = timelineScriptParser;
        this.mediaProbeService = mediaProbeService;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("ShotstackRenderProvider: job={}, profile={}", jobId, profile);

        if (!properties.isConfigured()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-503-020", 503020,
                            Map.of("en", "Shotstack API key not configured", "zh", "未配置 Shotstack API Key"),
                            "render", 503),
                    "Set render.providers.shotstack.api-key",
                    Map.of("jobId", jobId),
                    "en");
        }

        Map<String, Object> params = extractShotstackParams(aiScript);
        Optional<ObjectNode> payload = timelineMapper.toEditPayload(aiScript, params);
        if (payload.isEmpty()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-400-020", 400020,
                            Map.of("en", "Timeline not mappable to Shotstack", "zh", "时间线无法映射到 Shotstack"),
                            "render", 400),
                    "Missing video clip with storage URI",
                    Map.of("jobId", jobId),
                    "en");
        }

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path outputPath = outputDir.resolve("output.mp4");

            String renderId = apiClient.submitRender(payload.get());
            ShotstackApiClient.ShotstackRenderStatus status = apiClient.pollUntilDone(renderId);
            if (!status.success() || status.renderUrl() == null) {
                throw new IllegalStateException("Shotstack render failed: " + status.error());
            }
            apiClient.downloadTo(status.renderUrl(), outputPath);

            RenderPreset preset = RenderPreset.fromProfile(profile);
            long durationSec = 30L;
            var probe = mediaProbeService.probeAbsolute(jobId, outputPath.toString());
            if (probe.valid() && probe.durationMs() > 0) {
                durationSec = Math.max(1L, Math.round(probe.durationMs() / 1000.0));
            }

            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                    durationSec,
                    "mp4",
                    preset.width() + "x" + preset.height());
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("Shotstack render failed job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-020", 500520,
                            Map.of("en", "Shotstack cloud render failed", "zh", "Shotstack 云渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId),
                    "en");
        }
    }

    private Map<String, Object> extractShotstackParams(String script) {
        Optional<TimelineSpec> spec = timelineScriptParser.parse(script);
        if (spec.isEmpty()) {
            return Map.of();
        }
        for (var track : spec.get().tracks()) {
            if (track.clips() == null) {
                continue;
            }
            for (var clip : track.clips()) {
                if (clip.effects() == null) {
                    continue;
                }
                for (TimelineClipEffect effect : clip.effects()) {
                    if ("video.shotstack_template".equals(effect.effectKey())) {
                        return effect.parameters() != null ? effect.parameters() : Map.of();
                    }
                }
            }
        }
        return Map.of("resolution", "hd");
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("shotstack_social_1080p", "shotstack_social_720p");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        if (!properties.isConfigured()) {
            return EnvironmentValidationResult.failed(
                    "Shotstack enabled but render.providers.shotstack.api-key is empty");
        }
        return EnvironmentValidationResult.ok();
    }
}
