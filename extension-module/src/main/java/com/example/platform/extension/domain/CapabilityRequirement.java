package com.example.platform.extension.domain;

import java.util.List;
import java.util.Objects;

/**
 * #16 (C4): explicit consumer-side capability requirement.
 *
 * <p>Expresses "what capability the consumer needs" — never "which plugin".
 * Used by future consumers (Operation, Recipe, Skill, Agent, MCP, RenderPlan,
 * Marketplace packages). Bounded: CapabilityId + compatible contract version
 * range + required/optional + optional alternatives. No SAT solver.
 */
public record CapabilityRequirement(
        CapabilityId capabilityId,
        ContractVersionRange contractRange,
        boolean required,
        List<CapabilityId> alternatives) {

    public CapabilityRequirement {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractRange, "contractRange");
        if (alternatives == null) alternatives = List.of();
        alternatives = List.copyOf(alternatives);
        // alternatives must be distinct from the primary id
        alternatives.forEach(a -> {
            if (a.equals(capabilityId)) {
                throw new IllegalArgumentException("alternative duplicates primary capability: " + a);
            }
        });
    }

    public static CapabilityRequirement of(CapabilityId id, ContractVersionRange range) {
        return new CapabilityRequirement(id, range, true, List.of());
    }

    public static CapabilityRequirement optional(CapabilityId id, ContractVersionRange range) {
        return new CapabilityRequirement(id, range, false, List.of());
    }

    public static CapabilityRequirement of(CapabilityId id, ContractVersionRange range,
                                           boolean required, List<CapabilityId> alternatives) {
        return new CapabilityRequirement(id, range, required, alternatives);
    }

    /** True when {@code version} satisfies this requirement's contract range. */
    public boolean accepts(ContractVersion version) {
        return contractRange.contains(version);
    }

    @Override
    public String toString() {
        return (required ? "requires " : "optional ") + capabilityId + " " + contractRange;
    }
}
