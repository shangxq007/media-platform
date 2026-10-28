package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import java.util.Objects;

/**
 * Production entry into typed-chain stage #21 (provider-neutral execution planning).
 *
 * <p>This is the application-facing boundary that turns a #20 render planning
 * result into the #21 structural execution plan:
 *
 * <pre>
 *   RenderPlanningResult (status must be PLANNABLE — guarded by #21)
 *     → ExecutionPlanningEntry.plan(...)   (fail closed, typed reason on rejection)
 *     → ExecutionRequirement
 *     → LogicalExecutionGraph
 *     → PhysicalExecutionPlan
 * </pre>
 *
 * <p>Boundary contract:
 * <ul>
 *   <li>The PLANNABLE gate is owned by #20 ({@code RenderPlanStatus}); this entry
 *       does not redefine it. Any result whose status != PLANNABLE is rejected
 *       with {@link ExecutionPlanningFailureReason#RENDER_PLANNING_RESULT_NOT_PLANNABLE}
 *       and no logical or physical plan is produced.</li>
 *   <li>{@link ExecutionPlanId} is caller-supplied plan identity, never derived
 *       from semantic content or the plan fingerprint.</li>
 *   <li>No provider/worker/device/queue binding and no mutable runtime read:
 *       provider binding is stage #22 and is deliberately NOT invoked here.</li>
 * </ul>
 *
 * <p>Scope: stage #21 only. Compatibility/feasibility and the provider-bound
 * executable task graph (#22) consume the returned physical plan through their
 * own entries; runtime lowering/adapter execution (#15/#16) is out of scope.
 */
public final class PhysicalPlanningEntryService {

    /**
     * Plans a #20 render result into the #21 structural execution plan.
     *
     * @throws com.example.platform.execution.planning.ExecutionPlanningException
     *         when the render result is not PLANNABLE, or when the physical plan
     *         postconditions cannot be established (fail closed)
     */
    public ExecutionPlanningEntry.PlanningResult plan(
            RenderPlanningResult renderResult, ExecutionPlanId planId) {
        Objects.requireNonNull(renderResult, "renderResult");
        Objects.requireNonNull(planId, "planId");
        return ExecutionPlanningEntry.plan(renderResult, planId);
    }

    /**
     * Plans a #20 render result and projects it onto the platform admission
     * contract owned by this stage.
     *
     * <p>The underlying adapter re-runs the guarded #21 entry (same PLANNABLE
     * gate and structural checks) and preserves the exact plan fingerprint.
     */
    public PlatformExecutionPlan planForAdmission(
            RenderPlanningResult renderResult, ExecutionPlanId planId, AdmissionIdentity identity) {
        Objects.requireNonNull(renderResult, "renderResult");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(identity, "identity");
        return RenderPlatformExecutionPlanAdapter.adapt(
                renderResult,
                planId,
                identity.scope(),
                identity.idempotencyKey(),
                identity.requestHash(),
                identity.quota(),
                identity.audit());
    }

    /** Plans an Artifact-sourced capability in the same #21 model consumed by #22. */
    public PhysicalExecutionPlan planArtifactCapability(ArtifactCapabilityRequest request, ExecutionPlanId planId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(planId, "planId");
        String semantic = PhysicalExecutionPlanDigest.sha256(request.canonical());
        var node = new com.example.platform.render.domain.renderplan.RenderNodeId("artifact-capability-" + semantic);
        var step = new com.example.platform.execution.domain.ExecutionStepId("artifact-capability-" + semantic);
        String logical = "artifact-capability-" + semantic;
        var position = request.parameters().position();
        var rate = com.example.platform.shared.time.FrameRate.of(1, 1);
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                position, position.add(com.example.platform.shared.time.MediaTime.ofTicks(1, 1)), rate);
        var window = new com.example.platform.render.domain.renderplan.RenderSampleWindow(position, position, rate);
        var input = new ExecutionIoProjection.InputBinding(
                new com.example.platform.execution.domain.ExecutionInputId("source-" + semantic),
                logical, step, node, null, null, null, null, new com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact(
                        request.source().artifactId(),request.source().contentDigest()), window);
        var output = new ExecutionIoProjection.OutputDeclaration(
                new com.example.platform.execution.domain.ExecutionOutputId("image-" + semantic), logical, node,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(new com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation(
                        com.example.platform.render.domain.renderplan.RenderOutputRole.DERIVED_MEDIA)));
        var capability = com.example.platform.extension.domain.CapabilityRequirement.of(
                com.example.platform.shared.capability.CapabilityId.of(request.capabilityId()),
                com.example.platform.shared.capability.ContractVersionRange.exactly(
                        com.example.platform.shared.capability.ContractVersion.of(1, 0)));
        // This intent supplies no resource/runtime preferences. #21 must not manufacture GPU,
        // memory or CPU constraints; #22/provider registration and the sandbox enforce execution.
        var unit = new PhysicalExecutionPlan.PhysicalPlanUnit(step, logical, node,
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Transform(), "frame-extract",
                java.util.List.of(input), java.util.List.of(output), java.util.List.of(), window, null,
                java.util.List.of(new ExecutionIoProjection.CapabilityRequirementRef(capability)),
                java.util.List.of(), extent, true, request);
        var units = java.util.List.of(unit);
        var schema = com.example.platform.execution.domain.ExecutionPlanSchemaVersion.V1;
        var fingerprint = new com.example.platform.render.domain.renderplan.RenderPlanFingerprint(semantic);
        return new PhysicalExecutionPlan("physical-execution-plan-v1", planId, schema, fingerprint,
                units, extent, PhysicalExecutionPlanDigest.compute("physical-execution-plan-v1", schema, units, fingerprint, extent));
    }

    /**
     * Typed admission identity required to build the platform admission
     * contract. Every component is caller-supplied — nothing is derived from
     * render semantics.
     */
    public record AdmissionIdentity(
            PlatformExecutionPlan.Scope scope,
            String idempotencyKey,
            String requestHash,
            PlatformExecutionPlan.EntitlementQuotaSnapshot quota,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {

        public AdmissionIdentity {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(requestHash, "requestHash");
            Objects.requireNonNull(quota, "quota");
            Objects.requireNonNull(audit, "audit");
        }
    }
}
