package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import java.util.Optional;

/** Server-owned lookup for an immutable published Composition revision and its plan fingerprint. */
public interface CompositionPublishedRevisionAuthority {
    Optional<PublishedRevision> resolve(String tenantId, String workspaceId,
            String compositionId, String version);

    record PublishedRevision(TemplateWorkflow workflow, String planFingerprint) {
        public PublishedRevision {
            if (workflow == null || workflow.lifecycle() != com.example.platform.composition.domain.CompositionModels.Lifecycle.PUBLISHED
                    || workflow.revision() < 1) {
                throw new IllegalArgumentException("published immutable revision is required");
            }
            if (planFingerprint == null || planFingerprint.isBlank()) {
                throw new IllegalArgumentException("published plan fingerprint is required");
            }
        }
    }
}
