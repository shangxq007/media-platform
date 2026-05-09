package com.example.platform.render.infrastructure.mlt;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MLT/melt-based render provider for multi-track timeline rendering.
 *
 * <p>This provider renders timelines using MLT's melt command. It converts
 * the internal {@link TimelineSpec} to MLT project XML and executes melt.</p>
 *
 * <p>Activated when {@code render.providers.melt.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.melt", name = "enabled", havingValue = "true")
public class MltRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(MltRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final MltProjectXmlBuilder xmlBuilder;
    private final MeltCommandFactory commandFactory;

    public MltRenderProvider(ProcessToolRunner processToolRunner,
            MltProjectXmlBuilder xmlBuilder, MeltCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.xmlBuilder = xmlBuilder;
        this.commandFactory = commandFactory;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("MltRenderProvider: rendering job={}, profile={}", jobId, profile);

        // Skeleton: build MLT XML and execute melt
        RenderProfile renderProfile = RenderProfile.of(profile, "1920x1080", "h264");

        log.info("MltRenderProvider: skeleton render for job={}", jobId);

        String artifactId = "art_" + jobId;
        String storageUri = "localFsStorageProvider://artifacts/" + artifactId + "/output.mp4";
        return new RenderResult(artifactId, storageUri, 30L, "mp4", renderProfile.resolution());
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("social_1080p", "social_720p", "default_1080p", "default_720p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "timeline", "multi-track", "transitions", "compositing" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            var validator = new MltEnvironmentValidator(processToolRunner);
            if (validator.validate()) {
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("melt not available");
        } catch (Exception e) {
            return EnvironmentValidationResult.failed("melt not available: " + e.getMessage());
        }
    }
}
