package com.example.platform.shared.events;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

/**
 * Immutable Render-owned snapshot of the principal that requested a render.
 *
 * <p>The snapshot deliberately contains only stable canonical actor identity:
 * actor ID, frozen actor type, and tenant scope. It is not an identity lookup,
 * authorization principal, or notification audience and never carries roles,
 * email, provider identity, or subscriber semantics.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RenderInitiator.Principal.class, name = "PRINCIPAL"),
        @JsonSubTypes.Type(value = RenderInitiator.System.class, name = "SYSTEM")
})
public sealed interface RenderInitiator permits RenderInitiator.Principal, RenderInitiator.System {

    String actorId();

    ActorType actorType();

    String tenantId();

    /** Snapshot an explicitly resolved canonical actor. Absence is not SYSTEM. */
    static RenderInitiator from(CanonicalActor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        return restore(actor.actorType(), actor.actorId(), actor.tenantId());
    }

    /** Reconstruct the exact immutable snapshot from trusted persistence. */
    static RenderInitiator restore(ActorType actorType, String actorId, String tenantId) {
        Objects.requireNonNull(actorType, "actorType must not be null");
        return switch (actorType) {
            case USER, API_KEY_PRINCIPAL -> new Principal(actorId, actorType, tenantId);
            case SYSTEM -> new System(actorId, actorType, tenantId);
        };
    }

    /** Authenticated USER or API_KEY_PRINCIPAL; not necessarily a human audience. */
    record Principal(String actorId, ActorType actorType, String tenantId)
            implements RenderInitiator {

        public Principal {
            actorId = requireText(actorId, "actorId");
            tenantId = requireText(tenantId, "tenantId");
            Objects.requireNonNull(actorType, "actorType must not be null");
            if (actorType == ActorType.SYSTEM) {
                throw new IllegalArgumentException("Principal initiator cannot have SYSTEM actor type");
            }
        }
    }

    /** Explicit internal SYSTEM identity. It is never inferred from absence. */
    record System(String actorId, ActorType actorType, String tenantId)
            implements RenderInitiator {

        public System {
            actorId = requireText(actorId, "actorId");
            tenantId = requireText(tenantId, "tenantId");
            Objects.requireNonNull(actorType, "actorType must not be null");
            if (actorType != ActorType.SYSTEM) {
                throw new IllegalArgumentException("System initiator must have SYSTEM actor type");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
