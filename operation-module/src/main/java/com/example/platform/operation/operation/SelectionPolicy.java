package com.example.platform.operation.operation;

import java.util.Objects;

/**
 * Provider-neutral implementation selection policy
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, DOM-OPERATION-001 layer 3).
 *
 * <p>Expresses selection intent only; it never names a provider/plugin in a
 * public request. {@link PinnedInternal} is an internal resolve outcome, not a
 * caller-selected public input.</p>
 */
public sealed interface SelectionPolicy
        permits SelectionPolicy.Cheapest,
        SelectionPolicy.Fastest,
        SelectionPolicy.PreferDeterministic,
        SelectionPolicy.PinnedInternal {

    /** Prefer the implementation with the lowest cost. */
    record Cheapest() implements SelectionPolicy {
        public static final Cheapest INSTANCE = new Cheapest();
    }

    /** Prefer the implementation with the lowest latency. */
    record Fastest() implements SelectionPolicy {
        public static final Fastest INSTANCE = new Fastest();
    }

    /** Prefer a deterministic implementation. */
    record PreferDeterministic() implements SelectionPolicy {
        public static final PreferDeterministic INSTANCE = new PreferDeterministic();
    }

    /** Explicit internal pin of one implementation (internal resolve outcome only). */
    record PinnedInternal(String implementationId) implements SelectionPolicy {
        public PinnedInternal {
            Objects.requireNonNull(implementationId, "implementationId");
            if (implementationId.isBlank()) {
                throw new IllegalArgumentException("implementationId must not be blank");
            }
        }
    }
}
