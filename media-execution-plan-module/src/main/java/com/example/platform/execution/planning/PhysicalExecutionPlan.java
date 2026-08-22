package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlan (C16).
 *
 * <p>Provider-neutral structural plan: ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT
 * for V1. Units carry typed inputs/outputs/dependencies and propagate exact
 * requirement references — NO provider/worker/device binding, NO runtime queue
 * binding, NO live availability binding. FUSION/TEMPORAL_CHUNKING/N_TO_M/
 * SEMANTIC_REWRITE/COST_OPTIMIZATION are DEFERRED.
 */
public record PhysicalExecutionPlan(
        String formatVersion,
        RenderPlanFingerprint planFingerprint,
        List<PhysicalPlanUnit> units,
        PhysicalExecutionPlanDigest digest) {

    public PhysicalExecutionPlan {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(digest, "digest");
        units = List.copyOf(units);
    }

    /**
     * One physical plan unit = exactly one logical execution node (V1).
     * Provider-neutral; carries structural + propagated semantic references only.
     */
    public record PhysicalPlanUnit(
            String planUnitId,
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            String sourceRenderNodeKind,
            String operationKey,
            List<String> capabilityRequirementRefKeys,
            List<String> executionIntentRefKeys,
            List<String> inputSourceUnitIds,
            List<String> outputDeclarationKeys) {

        public PhysicalPlanUnit {
            Objects.requireNonNull(planUnitId, "planUnitId");
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            capabilityRequirementRefKeys = capabilityRequirementRefKeys == null
                    ? List.of() : List.copyOf(capabilityRequirementRefKeys);
            executionIntentRefKeys = executionIntentRefKeys == null
                    ? List.of() : List.copyOf(executionIntentRefKeys);
            inputSourceUnitIds = inputSourceUnitIds == null
                    ? List.of() : List.copyOf(inputSourceUnitIds);
            outputDeclarationKeys = outputDeclarationKeys == null
                    ? List.of() : List.copyOf(outputDeclarationKeys);
        }
    }
}
