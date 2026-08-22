package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlan (C16) — provider-neutral structural plan.
 *
 * <p>ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT. Each unit carries typed
 * semantic structure for #22 runtime lowering: typed inputs (exact #20
 * dependency + SourceArtifact semantics), typed output declarations (#20
 * output/materialization requirements + Intermediate/Final artifact
 * expectations), typed dependencies, exact temporal sample window,
 * propagated RenderExtent and typed requirement references. NO
 * provider/worker/device/queue/availability binding. Deterministic
 * cacheability metadata is declarative structural metadata only.
 *
 * <p>ExecutionPlanId (frozen ledger REUSE_AS_CANONICAL) is plan identity,
 * independent from semantic content digest. ExecutionPlanSchemaVersion
 * carries frozen version semantics.
 */
public record PhysicalExecutionPlan(
        String formatVersion,
        ExecutionPlanId planId,
        ExecutionPlanSchemaVersion schemaVersion,
        RenderPlanFingerprint planFingerprint,
        List<PhysicalPlanUnit> units,
        RenderExtent propagatedExtent,
        PhysicalExecutionPlanDigest digest) {

    public PhysicalExecutionPlan {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(digest, "digest");
        units = List.copyOf(units);
    }

    /**
     * One physical plan unit = exactly one logical execution node (V1).
     * Typed semantics throughout.
     */
    public record PhysicalPlanUnit(
            com.example.platform.execution.domain.ExecutionStepId stepId,
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            RenderNodeKind sourceRenderNodeKind,
            String operationKey,
            List<InputBinding> typedInputs,
            List<OutputDeclaration> typedOutputs,
            List<LogicalExecutionGraph.LogicalDependencyEdge> typedDependencies,
            RenderSampleWindow temporalWindow,
            com.example.platform.render.domain.renderplan.RenderExecutionCoverage executionCoverage,
            List<ExecutionIoProjection.CapabilityRequirementRef> capabilityRequirementRefs,
            List<ExecutionIoProjection.ExecutionIntentRef> executionIntentRefs,
            RenderExtent propagatedExtent,
            boolean deterministicallyCacheable) {

        public PhysicalPlanUnit {
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            Objects.requireNonNull(sourceRenderNodeKind, "sourceRenderNodeKind");
            typedInputs = typedInputs == null ? List.of() : List.copyOf(typedInputs);
            typedOutputs = typedOutputs == null ? List.of() : List.copyOf(typedOutputs);
            typedDependencies = typedDependencies == null ? List.of() : List.copyOf(typedDependencies);
            capabilityRequirementRefs = capabilityRequirementRefs == null
                    ? List.of() : List.copyOf(capabilityRequirementRefs);
            executionIntentRefs = executionIntentRefs == null
                    ? List.of() : List.copyOf(executionIntentRefs);
        }
    }
}
