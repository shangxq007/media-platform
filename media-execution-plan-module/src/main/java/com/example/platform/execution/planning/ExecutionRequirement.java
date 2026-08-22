package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 ExecutionRequirement — PURE DERIVED NORMALIZED PROJECTION (CR-02).
 *
 * <p>Derived exclusively from RenderPlan + validated RenderGraph declarations.
 * MUST NOT independently redeclare RenderExtent, CapabilityRequirement,
 * RenderExecutionRequirement, RenderOutputRequirement,
 * RenderMaterializationRequirement, or sample-window semantics — those remain
 * owned by their #20 canonical declarations and are referenced here.
 *
 * <p>Correlation/request/job/trace/createdAt identity is PROVENANCE_ONLY and
 * EXCLUDED_FROM_SEMANTIC_DIGEST.
 */
public record ExecutionRequirement(
        RenderPlanFingerprint planFingerprint,
        RenderExtent requestedExtent,
        List<CapabilityRequirementRef> capabilityRequirementRefs,
        List<ExecutionIntentRef> executionIntentRefs,
        ProvenanceOnlyContext provenance) {

    public ExecutionRequirement {
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(capabilityRequirementRefs, "capabilityRequirementRefs");
        Objects.requireNonNull(executionIntentRefs, "executionIntentRefs");
        capabilityRequirementRefs = List.copyOf(capabilityRequirementRefs);
        executionIntentRefs = List.copyOf(executionIntentRefs);
        // requestedExtent may be null when the request carries no extent
        // (non-extent-authoritative planning scenario); when present it is the
        // typed single authority.
    }

    /**
     * Reference to a declared CapabilityRequirement — never a re-declaration,
     * never a CapabilityId-only downgrade.
     */
    public record CapabilityRequirementRef(
            String sourceRenderNodeId,
            int declarationIndex,
            com.example.platform.extension.domain.CapabilityId capabilityId,
            com.example.platform.extension.domain.ContractVersionRange contractRange,
            boolean required,
            List<com.example.platform.extension.domain.CapabilityId> alternatives) {
        public CapabilityRequirementRef {
            capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            contractRange = Objects.requireNonNull(contractRange, "contractRange");
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        }
    }

    /**
     * Reference to a declared RenderExecutionRequirement — 1:1 derived, never
     * redefined. determinismClass is the upstream authority value.
     */
    public record ExecutionIntentRef(
            String sourceRenderNodeId,
            int declarationIndex,
            com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass determinismClass,
            boolean sandboxedIntent) {
        public ExecutionIntentRef {
            determinismClass = Objects.requireNonNull(determinismClass, "determinismClass");
        }
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
        var capabilityRefs = new java.util.ArrayList<CapabilityRequirementRef>();
        var intentRefs = new java.util.ArrayList<ExecutionIntentRef>();
        for (var node : plan.nodes()) {
            var capReqs = node.capabilityRequirements();
            if (capReqs != null) {
                for (int i = 0; i < capReqs.size(); i++) {
                    var cr = capReqs.get(i);
                    capabilityRefs.add(new CapabilityRequirementRef(
                            node.id().value(), i, cr.capabilityId(), cr.contractRange(),
                            cr.required(), cr.alternatives()));
                }
            }
            var execReqs = node.executionRequirements();
            if (execReqs != null) {
                for (int i = 0; i < execReqs.size(); i++) {
                    var er = execReqs.get(i);
                    intentRefs.add(new ExecutionIntentRef(
                            node.id().value(), i, er.determinism(), er.sandboxedIntent()));
                }
            }
        }
        RenderExtent extent = plan.request() != null ? plan.request().extent() : null;
        return new ExecutionRequirement(
                plan.fingerprint(), extent, capabilityRefs, intentRefs, ProvenanceOnlyContext.absent());
    }
}
