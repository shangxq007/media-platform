package com.example.platform.operation.invocation;

import com.example.platform.shared.authorization.CanonicalActor;

import java.util.Objects;

/**
 * Trusted authentication and execution context for one invocation.
 *
 * <p>Semantic intent remains entirely in the {@code OperationRequest}. The
 * actor is the sole authority-bearing identity in this context.</p>
 */
@org.springframework.modulith.NamedInterface("invocation")
public record OperationInvocationContext(
        CanonicalActor actor,
        String invocationId,
        Provenance provenance) {

    private static final int MAX_INVOCATION_ID_LENGTH = 256;

    public OperationInvocationContext {
        Objects.requireNonNull(actor, "actor");
        requireOptionalText(invocationId, "invocationId", MAX_INVOCATION_ID_LENGTH, false);
        Objects.requireNonNull(provenance, "provenance");
    }

    /**
     * Bounded observation metadata only. Neither field is authoritative and
     * neither may change, replace, or supplement the canonical actor.
     */
    @org.springframework.modulith.NamedInterface("invocation")
    public record Provenance(String correlationId, String origin) {

        private static final int MAX_CORRELATION_ID_LENGTH = 256;
        private static final int MAX_ORIGIN_LENGTH = 128;

        public Provenance {
            requireOptionalText(correlationId, "correlationId", MAX_CORRELATION_ID_LENGTH, true);
            requireOptionalText(origin, "origin", MAX_ORIGIN_LENGTH, true);
        }
    }

    private static void requireOptionalText(String value, String name, int maximumLength, boolean optional) {
        if (value == null) {
            if (optional) {
                return;
            }
            throw new IllegalArgumentException(name + " required");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
    }
}
