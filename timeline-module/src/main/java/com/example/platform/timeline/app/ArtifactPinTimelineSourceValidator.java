package com.example.platform.timeline.app;

import com.example.platform.artifact.app.ArtifactSourcePinAuthority;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Artifact-native implementation of the published {@link TimelineSourceValidation} contract
 * (TIMELINE_SOURCE_VALIDATION_ARTIFACT_PIN_V1, decision TYPED-ARTIFACT-PATH-1B-001).
 *
 * <p>Validates the binding's immutable Artifact pin — identity, tenant/project scope, content
 * digest and usable lifecycle — through {@link ArtifactSourcePinAuthority}. It does <b>not</b>
 * resolve legacy Media identities and does <b>not</b> perform stream-level validation
 * (see {@code docs/architecture/timeline-source-validation-contract-v27.md}).
 *
 * <p>This bean is the default-profile authority. The retired media-backed
 * {@code TimelineSourceReferenceValidator} remains fenced behind {@code legacy-media-disabled},
 * and this implementation is disabled in that profile so exactly one implementation is active.
 *
 * <p>Resolution is scope-bound: without an explicit tenant and project the validator fails closed
 * instead of consulting ambient context or falling back to a structural-only verdict.
 */
@Component
@Profile("!legacy-media-disabled")
public class ArtifactPinTimelineSourceValidator implements TimelineSourceValidation {

    private static final String SCOPE_REQUIRED =
            "source artifact pin validation requires tenant and project scope";

    private final ArtifactSourcePinAuthority pins;

    public ArtifactPinTimelineSourceValidator(ArtifactSourcePinAuthority pins) {
        this.pins = Objects.requireNonNull(pins, "pins");
    }

    @Override
    public ValidationResult validate(MediaStreamSourceBinding binding) {
        if (binding == null) {
            return invalid("source binding required");
        }
        // The context-free form carries no scope, and Artifact pin resolution is scope-bound.
        return invalid(SCOPE_REQUIRED);
    }

    @Override
    public ValidationResult validate(
            MediaStreamSourceBinding binding,
            String expectedTenantId,
            String expectedProjectId,
            TrackType expectedTrackType) {
        if (binding == null) {
            return invalid("source binding required");
        }
        if (expectedTenantId == null
                || expectedTenantId.isBlank()
                || expectedProjectId == null
                || expectedProjectId.isBlank()) {
            return invalid(SCOPE_REQUIRED);
        }
        var resolution = pins.resolvePin(
                expectedTenantId, expectedProjectId, binding.artifactId(), binding.contentDigest());
        String pin = binding.artifactId().value() + "@" + binding.contentDigest().canonicalValue();
        return switch (resolution.outcome()) {
            case RESOLVED -> valid();
            case UNKNOWN_ARTIFACT ->
                    invalid("source artifact pin is unknown: " + pin);
            case OUT_OF_SCOPE ->
                    invalid("source artifact pin is outside target project scope: " + pin);
            case NOT_USABLE ->
                    invalid("source artifact pin is not usable: " + pin);
            case PIN_MISMATCH ->
                    invalid("source artifact pin content digest mismatch: " + pin
                            + " recorded=" + resolution.artifactDigest());
        };
    }

    private static ValidationResult valid() {
        return new ValidationResult(true, List.of());
    }

    private static ValidationResult invalid(String violation) {
        return new ValidationResult(false, List.of(violation));
    }
}
