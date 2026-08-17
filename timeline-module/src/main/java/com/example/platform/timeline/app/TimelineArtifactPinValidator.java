package com.example.platform.timeline.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C11/C12): Timeline Artifact pin
 * reference-integrity validation — closes Roadmap #14 debt.
 *
 * <p>Application/reference-integrity boundary (NOT canonical JSON shape
 * validation): for every exact Artifact pin in a NEW revision, verify
 *
 * <ul>
 *   <li>ArtifactId exists</li>
 *   <li>Artifact belongs to the expected tenant (cross-tenant fails closed)</li>
 *   <li>Artifact.contentDigest == pinned ContentDigest</li>
 * </ul>
 *
 * <p>Timeline never reads artifact DB rows directly; it queries through the
 * Artifact-facing query contract ({@link ArtifactQueryService}). Missing or
 * mismatched pins FAIL CLOSED before the revision transaction commits.</p>
 */
@Component
public class TimelineArtifactPinValidator {

    private final ArtifactQueryService artifactQueryService;

    public TimelineArtifactPinValidator(ArtifactQueryService artifactQueryService) {
        this.artifactQueryService = artifactQueryService;
    }

    /**
     * Validate all pins; returns violations (empty = valid). Never throws.
     */
    public ValidationResult validate(String tenantId, List<TimelineArtifactPinExtractor.ArtifactPin> pins) {
        List<String> violations = new ArrayList<>();
        if (pins == null || pins.isEmpty()) {
            return new ValidationResult(true, List.of());
        }
        for (TimelineArtifactPinExtractor.ArtifactPin pin : pins) {
            validatePin(tenantId, pin, violations);
        }
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    private void validatePin(String tenantId, TimelineArtifactPinExtractor.ArtifactPin pin,
            List<String> violations) {
        ArtifactId artifactId = pin.artifactId();
        Optional<Artifact> artifact = artifactQueryService.getArtifact(tenantId, artifactId);
        if (artifact.isEmpty()) {
            violations.add("Artifact does not exist for tenant " + tenantId + ": " + artifactId.value());
            return;
        }
        ContentDigest recorded = artifact.get().contentDigest();
        if (!recorded.matches(pin.contentDigest())) {
            violations.add("Artifact content digest mismatch for " + artifactId.value()
                    + ": pinned=" + pin.contentDigest().canonicalValue()
                    + " recorded=" + recorded.canonicalValue());
        }
    }

    public record ValidationResult(boolean valid, List<String> violations) {}
}
