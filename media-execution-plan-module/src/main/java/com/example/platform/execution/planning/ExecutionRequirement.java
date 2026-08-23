package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 ExecutionRequirement — PURE DERIVED NORMALIZED PROJECTION (CR-02).
 *
 * <p>Derived exclusively from RenderPlan + validated RenderGraph declarations.
 * MUST NOT independently redeclare RenderExtent, CapabilityRequirement,
 * RenderExecutionRequirement, RenderOutputRequirement,
 * RenderMaterializationRequirement, or sample-window semantics — those remain
 * owned by their #20 canonical declarations and are referenced here as typed
 * values.
 *
 * <p>Correlation/request/job/trace/createdAt identity is PROVENANCE_ONLY and
 * EXCLUDED_FROM_SEMANTIC_DIGEST.
 */
public record ExecutionRequirement(
        RenderPlanFingerprint planFingerprint,
        RenderExtent requestedExtent,
        List<ExecutionIoProjection.CapabilityRequirementRef> capabilityRequirementRefs,
        List<ExecutionIoProjection.ExecutionIntentRef> executionIntentRefs,
        ProvenanceOnlyContext provenance) {

    public ExecutionRequirement {
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(capabilityRequirementRefs, "capabilityRequirementRefs");
        Objects.requireNonNull(executionIntentRefs, "executionIntentRefs");
        capabilityRequirementRefs = List.copyOf(capabilityRequirementRefs.stream()
                .sorted(Comparator.comparing(ref -> Canonical.capability(ref.declaration())))
                .toList());
        executionIntentRefs = List.copyOf(executionIntentRefs.stream()
                .sorted(Comparator.comparing(ref -> Canonical.executionIntent(ref.declaration())))
                .toList());
        // requestedExtent may be null when the request carries no extent; when
        // present it is the typed single authority.
    }

    /** Provenance-only identity — excluded from semantic digest. */
    public record ProvenanceOnlyContext(String correlationId, String createdAt) {
        public static ProvenanceOnlyContext absent() {
            return new ProvenanceOnlyContext(null, null);
        }
    }

    /** Normalization entry point: derive the projection from a validated RenderPlan. */
    public static ExecutionRequirement derive(RenderPlan plan) {
        Objects.requireNonNull(plan, "plan");
        var capabilityRefs = new java.util.ArrayList<ExecutionIoProjection.CapabilityRequirementRef>();
        var intentRefs = new java.util.ArrayList<ExecutionIoProjection.ExecutionIntentRef>();
        for (var node : plan.nodes()) {
            if (node.capabilityRequirements() != null) {
                for (var cr : node.capabilityRequirements()) {
                    capabilityRefs.add(new ExecutionIoProjection.CapabilityRequirementRef(cr));
                }
            }
            if (node.executionRequirements() != null) {
                for (var er : node.executionRequirements()) {
                    intentRefs.add(new ExecutionIoProjection.ExecutionIntentRef(er));
                }
            }
        }
        RenderExtent extent = plan.request() != null ? plan.request().extent() : null;
        return new ExecutionRequirement(
                plan.fingerprint(), extent, capabilityRefs, intentRefs, ProvenanceOnlyContext.absent());
    }
}
