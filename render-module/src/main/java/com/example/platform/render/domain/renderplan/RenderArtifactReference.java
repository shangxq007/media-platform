package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;

/**
 * Typed artifact reference (C15). Three roles only:
 * <ul>
 *   <li>{@link SourceArtifact} — pinned immutable source content.</li>
 *   <li>{@link IntermediateArtifactExpectation} — logical/planned intermediate identity.</li>
 *   <li>{@link FinalArtifactExpectation} — expected final output role.</li>
 * </ul>
 * RenderPlan/RenderGraph never own artifact bytes.
 */
public sealed interface RenderArtifactReference permits
        RenderArtifactReference.SourceArtifact,
        RenderArtifactReference.IntermediateArtifactExpectation,
        RenderArtifactReference.FinalArtifactExpectation {

    /**
     * Canonical variant key for deterministic edge ordering.
     */
    String variantKey();

    /** Pinned immutable source content consumed (from MediaStreamSourceBinding). */
    record SourceArtifact(ArtifactId artifactId, ContentDigest contentDigest)
            implements RenderArtifactReference {
        public SourceArtifact {
            if (artifactId == null || contentDigest == null) {
                throw new IllegalArgumentException("SourceArtifact artifactId and contentDigest required");
            }
        }

        @Override
        public String variantKey() {
            return "SOURCE_ARTIFACT:" + artifactId + ":" + contentDigest;
        }
    }

    /** Logical/planned identity for a to-be-produced intermediate. */
    record IntermediateArtifactExpectation(LogicalArtifactId logicalId, RenderOutputRole role)
            implements RenderArtifactReference {
        public IntermediateArtifactExpectation {
            if (logicalId == null || role == null) {
                throw new IllegalArgumentException("IntermediateArtifactExpectation logicalId and role required");
            }
        }

        @Override
        public String variantKey() {
            return "INTERMEDIATE_ARTIFACT:" + logicalId + ":" + role;
        }
    }

    /** Expected final output role. */
    record FinalArtifactExpectation(RenderOutputRole role)
            implements RenderArtifactReference {
        public FinalArtifactExpectation {
            if (role == null) {
                throw new IllegalArgumentException("FinalArtifactExpectation role required");
            }
        }

        @Override
        public String variantKey() {
            return "FINAL_ARTIFACT:" + role;
        }
    }
}
