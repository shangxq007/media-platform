package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import java.util.List;
import java.util.Objects;

/**
 * The durable INPUTS of one provider-bound executable task graph.
 *
 * <p>A {@code ProviderBoundExecutableTaskGraph} is an in-process proof artifact: its
 * tasks carry kernel-emitted opaque compatibility proofs and evaluator provenance, and
 * its construction validates task membership against the exact in-memory feasibility
 * view. It therefore CANNOT be serialized and rehydrated; what is durable is exactly
 * this tuple of pure typed values plus the digest the binding is expected to produce.
 *
 * <p>Re-derivation is deterministic: the same inputs yield the same graph and the same
 * {@link ExecutableTaskGraphDigest}, so the digest is the stable reference that ties a
 * durable record to the work it denotes.
 *
 * @param physicalPlan                    the canonical #21 physical execution plan
 * @param candidates                      the declared Stage-1 provider candidates
 * @param transitionDeclarations          the declared provider boundary transitions
 * @param expectedExecutableTaskGraphDigest the digest the binding must reproduce
 */
public record BoundGraphInputs(
        PhysicalExecutionPlan physicalPlan,
        List<ProviderCandidate> candidates,
        List<ProviderBoundaryCompatibilityDeclaration> transitionDeclarations,
        ExecutableTaskGraphDigest expectedExecutableTaskGraphDigest) {

    public BoundGraphInputs {
        Objects.requireNonNull(physicalPlan, "physicalPlan");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(transitionDeclarations, "transitionDeclarations");
        Objects.requireNonNull(expectedExecutableTaskGraphDigest, "expectedExecutableTaskGraphDigest");
        candidates = List.copyOf(candidates);
        transitionDeclarations = List.copyOf(transitionDeclarations);
    }
}
