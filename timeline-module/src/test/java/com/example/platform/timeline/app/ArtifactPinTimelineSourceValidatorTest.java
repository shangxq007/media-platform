package com.example.platform.timeline.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.artifact.app.ArtifactSourcePinAuthority;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused coverage of the Artifact-native source validation contract
 * (TIMELINE_SOURCE_VALIDATION_ARTIFACT_PIN_V1).
 */
class ArtifactPinTimelineSourceValidatorTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String DIGEST = "a".repeat(64);
    private static final ArtifactId ARTIFACT = new ArtifactId("art-1");

    /** Records the requested pin so the test proves the validator delegates identity + digest. */
    private static final class RecordingAuthority implements ArtifactSourcePinAuthority {
        private final Outcome outcome;
        private final List<String> requests = new ArrayList<>();

        RecordingAuthority(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public PinResolution resolvePin(
                String tenantId, String projectId, ArtifactId artifactId, ContentDigest pinnedDigest) {
            requests.add(tenantId + "|" + projectId + "|" + artifactId.value() + "|"
                    + pinnedDigest.canonicalValue());
            return new PinResolution(outcome, artifactId, tenantId, projectId,
                    pinnedDigest.canonicalValue(), DIGEST);
        }
    }

    private static MediaStreamSourceBinding binding() {
        return new MediaStreamSourceBinding(
                new MediaAssetId("asset-1"),
                new MediaStreamId("stream-1"),
                ARTIFACT,
                ContentDigest.sha256(DIGEST),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofMillis(1_000)));
    }

    @Test
    void validWhenArtifactPinResolvesInScope() {
        var authority = new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.RESOLVED);
        var validator = new ArtifactPinTimelineSourceValidator(authority);

        var result = validator.validate(binding(), TENANT, PROJECT, TrackType.VIDEO);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(authority.requests)
                .containsExactly(TENANT + "|" + PROJECT + "|art-1|" + DIGEST);
    }

    @Test
    void unknownPinIsReportedAsSourceReferenceViolation() {
        var validator = new ArtifactPinTimelineSourceValidator(
                new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.UNKNOWN_ARTIFACT));

        var result = validator.validate(binding(), TENANT, PROJECT, TrackType.VIDEO);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0)).contains("unknown").contains("art-1");
    }

    @Test
    void outOfScopePinIsReported() {
        var validator = new ArtifactPinTimelineSourceValidator(
                new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.OUT_OF_SCOPE));

        var result = validator.validate(binding(), TENANT, PROJECT, TrackType.VIDEO);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations().get(0)).contains("outside target project scope");
    }

    @Test
    void unusablePinIsReported() {
        var validator = new ArtifactPinTimelineSourceValidator(
                new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.NOT_USABLE));

        var result = validator.validate(binding(), TENANT, PROJECT, TrackType.VIDEO);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations().get(0)).contains("not usable");
    }

    @Test
    void digestMismatchIsReportedWithRecordedDigest() {
        var validator = new ArtifactPinTimelineSourceValidator(
                new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.PIN_MISMATCH));

        var result = validator.validate(binding(), TENANT, PROJECT, TrackType.VIDEO);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations().get(0)).contains("content digest mismatch");
    }

    @Test
    void failsClosedWithoutScopeInsteadOfStructuralOnlyPass() {
        var authority = new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.RESOLVED);
        var validator = new ArtifactPinTimelineSourceValidator(authority);

        assertThat(validator.validate(binding()).valid()).isFalse();
        assertThat(validator.validate(binding(), null, PROJECT, TrackType.VIDEO).valid()).isFalse();
        assertThat(validator.validate(binding(), TENANT, " ", TrackType.VIDEO).valid()).isFalse();
        assertThat(authority.requests).isEmpty();
    }

    @Test
    void nullBindingIsRejected() {
        var validator = new ArtifactPinTimelineSourceValidator(
                new RecordingAuthority(ArtifactSourcePinAuthority.Outcome.RESOLVED));

        assertThat(validator.validate(null).valid()).isFalse();
        assertThat(validator.validate(null, TENANT, PROJECT, null).valid()).isFalse();
    }
}
