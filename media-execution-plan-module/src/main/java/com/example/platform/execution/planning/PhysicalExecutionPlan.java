package com.example.platform.execution.planning;

import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.MediaTime;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlan (C16, Blocker F).
 *
 * <p>Provider-neutral structural plan. ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT.
 * Each unit carries the typed semantic structure required for #22 runtime
 * lowering: typed inputs (exact #20 dependency/artifact semantics), typed
 * output declarations (#20 output/materialization requirements), typed
 * dependencies, exact temporal sample window, propagated RenderExtent and
 * typed requirement references. NO provider/worker/device/queue/availability
 * binding. NO runtime cache semantics — deterministic cacheability metadata is
 * declarative structural metadata only.
 *
 * <p>ExecutionPlanId is plan identity, distinct from semantic content digest.
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
     * Typed semantics throughout; no string-keyed loss.
     */
    public record PhysicalPlanUnit(
            String planUnitId,
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            RenderNodeKind sourceRenderNodeKind,
            String operationKey,
            List<InputBinding> typedInputs,
            List<OutputDeclaration> typedOutputs,
            List<LogicalExecutionGraph.LogicalDependencyEdge> typedDependencies,
            RenderSampleWindow temporalWindow,
            List<ExecutionIoProjection.CapabilityRequirementRef> capabilityRequirementRefs,
            List<ExecutionIoProjection.ExecutionIntentRef> executionIntentRefs,
            RenderExtent propagatedExtent,
            boolean deterministicallyCacheable) {

        public PhysicalPlanUnit {
            Objects.requireNonNull(planUnitId, "planUnitId");
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

    /** Plan identity — NOT a semantic digest. Provenance-level only. */
    public record ExecutionPlanId(String value) {
        public ExecutionPlanId {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Plan schema version — semantic format version of the plan structure. */
    public record ExecutionPlanSchemaVersion(int major, int minor) {
        public ExecutionPlanSchemaVersion {
            if (major < 0 || minor < 0) {
                throw new IllegalArgumentException("schema version must be non-negative");
            }
        }

        public String canonical() {
            return major + "." + minor;
        }
    }
}
