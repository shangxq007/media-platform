package com.example.platform.timeline.app;

import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Objects;

/** Trusted identity inputs re-authorized by the sole canonical Timeline writer. */
public record TimelineMutationContext(
        String tenantId,
        String projectId,
        CanonicalActor actor) {

    public TimelineMutationContext {
        requireText(tenantId, "tenantId");
        requireText(projectId, "projectId");
        Objects.requireNonNull(actor, "authenticated canonical actor required");
        if (!tenantId.equals(actor.tenantId())) {
            throw new IllegalArgumentException(
                    "canonical actor tenant must equal mutation tenant");
        }
    }

    public String authorUserId() {
        return actor.actorId();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
    }
}
