package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.providers.ffmpeg", name = "enabled", havingValue = "true")
public class FFmpegRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(FFmpegRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final FFmpegCommandFactory commandFactory;

    public FFmpegRenderProvider(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("FFmpegRenderProvider: rendering job={}, profile={}", jobId, profile);

        RenderProfile renderProfile = RenderProfile.of(profile, "1920x1080", "h264");

        log.info("FFmpegRenderProvider: skeleton render for job={}", jobId);

        String artifactId = "art_" + jobId;
        String storageUri = "localFsStorageProvider://artifacts/" + artifactId + "/output.mp4";
        return new RenderResult(artifactId, storageUri, 30L, "mp4", renderProfile.resolution());
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("social_1080p", "social_720p", "default_1080p", "default_720p",
                "broadcast_4k", "proxy_480p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "h264", "h265", "mp4", "watermark", "subtitle-burn", "thumbnail", "probe",
                    "4k", "hdr", "dash", "hls" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "ffmpeg", List.of("-version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                String version = result.stdout().lines().findFirst().orElse("unknown");
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("FFmpeg returned non-zero exit code");
        } catch (Exception e) {
            return EnvironmentValidationResult.failed("FFmpeg not available: " + e.getMessage());
        }
    }
}
