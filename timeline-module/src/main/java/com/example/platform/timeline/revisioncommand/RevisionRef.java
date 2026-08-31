package com.example.platform.timeline.revisioncommand;

/**
 * REVISION_COMMAND_MODEL_V1 (RC15/RC16): typed mutable revision ref.
 * V1 identity = tenant + project scope + ref name (name IS identity; no stable-id +
 * mutable-name split). "main" is the conventional default ref only — no
 * hardcoded semantic authority. Ref is a mutable graph pointer, NOT Timeline
 * canonical content / Revision identity / content hash.
 */
public record RevisionRef(String tenantId, String projectId, String refId) {

    public static final String MAIN_REF = "main";

    public RevisionRef {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId required");
        }
        if (refId == null || refId.isBlank() || refId.length() > 64) {
            throw new IllegalArgumentException("refId required, bounded, non-blank (<=64)");
        }
    }

    public static RevisionRef main(String tenantId, String projectId) {
        return new RevisionRef(tenantId, projectId, MAIN_REF);
    }

    @Override
    public String toString() {
        return tenantId + ":" + projectId + ":" + refId;
    }
}
