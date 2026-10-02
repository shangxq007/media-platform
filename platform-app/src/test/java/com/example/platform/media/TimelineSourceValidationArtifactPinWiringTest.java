package com.example.platform.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.PlatformApplication;
import com.example.platform.artifact.app.ArtifactSourcePinAuthority;
import com.example.platform.media.api.MediaAssets;
import com.example.platform.media.api.MediaStreamQueries;
import com.example.platform.render.app.operation.TimelineMediaClipOperationService;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Runtime proof for the narrowed Timeline source-validation contract
 * (TIMELINE_SOURCE_VALIDATION_ARTIFACT_PIN_V1, decision TYPED-ARTIFACT-PATH-1B-001).
 *
 * <p>The real default-profile production graph must bind the published
 * {@link TimelineSourceValidation} port to exactly one Artifact-native implementation, resolved
 * through the Artifact authority, with no retired media authority backing it. The H8-frozen
 * {@link TimelineMediaClipOperationService} consumer must be present and wired to that port.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "app.security.jwt.secret-key=platform-api-context-test-secret-at-least-256-bits-long!!")
class TimelineSourceValidationArtifactPinWiringTest extends PostgresTestContainerSupport {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void defaultProfileBindsSourceValidationToTheArtifactNativeImplementationOnly() {
        assertThat(context.isActive()).isTrue();
        assertThat(context.getEnvironment().acceptsProfiles("legacy-media-disabled")).isFalse();

        String[] implementations = context.getBeanNamesForType(TimelineSourceValidation.class);
        assertThat(implementations).hasSize(1);
        assertThat(context.getBean(TimelineSourceValidation.class))
                .isInstanceOf(
                        com.example.platform.timeline.app.ArtifactPinTimelineSourceValidator.class);

        assertThat(context.getBeanNamesForType(ArtifactSourcePinAuthority.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TimelineMediaClipOperationService.class)).hasSize(1);

        assertThat(context.getBeanNamesForType(MediaAssets.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MediaStreamQueries.class)).isEmpty();
    }
}
