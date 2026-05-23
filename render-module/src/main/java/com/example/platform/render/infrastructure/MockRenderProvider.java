package com.example.platform.render.infrastructure;

import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock render provider for development and testing.
 *
 * <p>Simulates render execution with a random delay. Implements the full
 * {@link RenderProvider} SPI including capability checks and environment
 * validation.</p>
 *
 * <p>Only active in the {@code test} profile. In production, {@link JavaCVRenderProvider}
 * is used instead.</p>
 */
@Component
public class MockRenderProvider implements RenderProvider {
    private static final Logger log = LoggerFactory.getLogger(MockRenderProvider.class);

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("MockRenderProvider: rendering job={}, profile={}", jobId, profile);
        try {
            Thread.sleep(200 + (long) (Math.random() * 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String artifactId = Ids.newId("art");
        String resolution = profile.contains("720") ? "1280x720" : "1920x1080";
        String storageUri = "localFsStorageProvider://artifacts/" + artifactId + "/output.mp4";
        return new RenderResult(artifactId, storageUri, 30L, "mp4", resolution);
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("social_1080p", "social_720p", "default_1080p", "default_720p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "h264", "mp4", "watermark" -> true;
            case "h265", "4k", "hdr" -> false;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        log.info("MockRenderProvider: environment validation passed (mock)");
        return EnvironmentValidationResult.ok();
    }
}
