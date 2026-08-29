package com.example.platform.shared.usage;

import com.example.platform.shared.authorization.CanonicalActor;
import java.util.Objects;

/** Stable, credential-free principal snapshot for runtime usage attribution. */
public record CanonicalActorRef(String actorId, String actorType) {

    public CanonicalActorRef {
        actorId = requireNonBlank(actorId, "actorId");
        actorType = requireNonBlank(actorType, "actorType");
    }

    public static CanonicalActorRef from(CanonicalActor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        return new CanonicalActorRef(actor.actorId(), actor.actorType().name());
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }
}
