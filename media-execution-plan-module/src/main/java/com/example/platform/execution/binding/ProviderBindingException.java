package com.example.platform.execution.binding;

import java.util.Objects;

/**
 * Typed fail-closed carrier for typed-chain stage #22 binding.
 *
 * <p>The {@link Reason} is the machine contract; the message is diagnostic
 * only and is never a branching authority.
 */
public class ProviderBindingException extends RuntimeException {

    /** Active V1 reasons for an un-bindable physical plan. */
    public enum Reason {

        /** The plan requires execution but no provider candidate was supplied. */
        NO_CANDIDATES,

        /** No supplied candidate is statically feasible for one physical plan unit. */
        UNIT_UNBINDABLE,

        /** More than one candidate is statically feasible and V1 defines no selection policy. */
        UNIT_AMBIGUOUS,

        /** The provider-local composition evaluator did not prove ALLOWED for one unit. */
        COMPOSITION_FORBIDDEN,

        /** The physical plan shape is outside the bounded V1 binding surface. */
        PLAN_SHAPE_UNSUPPORTED,

        /** The exact producer/consumer binding pair has no compatible transition. */
        TRANSITION_INCOMPATIBLE,

        /** The exact producer/consumer binding pair has an unknown transition (fails closed). */
        TRANSITION_UNKNOWN
    }

    private final Reason reason;

    public ProviderBindingException(Reason reason, String detail) {
        super(reason.name() + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
