package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.execution.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles a ProviderBindingPlan and ProviderExecutionDocumentDrafts
 * into a RenderExecutionPlan.
 *
 * <p>v0: All steps are planning placeholders with executionReady=false.
 * The compiler does NOT execute providers, does NOT generate commands,
 * and does NOT mutate StorageRuntime or ProductRuntime.</p>
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
@Service
public class RenderExecutionPlanCompiler {

    private static final Logger log = LoggerFactory.getLogger(RenderExecutionPlanCompiler.class);

    /**
     * Compile a binding plan into an execution plan.
     *
     * @param bindingPlan   the provider binding plan
     * @param drafts        execution document drafts for bound nodes
     * @param policy        execution policy
     * @return the render execution plan
     */
    public RenderExecutionPlan compile(
            ProviderBindingPlan bindingPlan,
            List<ProviderExecutionDocumentDraft> drafts,
            ExecutionPolicy policy) {

        if (bindingPlan == null) {
            throw new IllegalArgumentException("ProviderBindingPlan must not be null");
        }
        if (drafts == null) {
            drafts = List.of();
        }
        if (policy == null) {
            policy = ExecutionPolicy.dryRun();
        }

        RenderExecutionPlanId planId = RenderExecutionPlanId.fromBindingPlan(
                bindingPlan.planId().toString(), policy.mode());

        List<RenderExecutionStep> steps = new ArrayList<>();
        List<RenderExecutionPlanFailureReason> failureReasons = new ArrayList<>();

        // Index drafts by binding node ID
        var draftIndex = drafts.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProviderExecutionDocumentDraft::bindingNodeId,
                        d -> d,
                        (a, b) -> a));

        String finalizeStepId = RenderExecutionStepId.of(
                planId.toString(), "FINALIZE_RENDER", null).toString();

        String finalVerifyStepId = null;
        String finalRegisterStepId = null;
        String finalLinkStepId = null;

        // Process each bound node
        for (ProviderBindingNode node : bindingPlan.nodes()) {
            if (node.isBound() && node.decision() != null && node.decision().selectedProvider() != null) {
                BoundProviderRef providerRef = node.decision().selectedProvider();
                ExecutionEnvironmentTarget target = resolveTarget(providerRef, policy);

                // Step 1: MATERIALIZE_INPUT for INPUT_MEDIA nodes
                String materializeStepId = null;
                if (node.artifactNodeType() == ArtifactNodeType.INPUT_MEDIA) {
                    materializeStepId = RenderExecutionStepId.of(
                            planId.toString(), "MATERIALIZE_INPUT", node.nodeId()).toString();
                    steps.add(new RenderExecutionStep(
                            materializeStepId,
                            RenderExecutionStepType.MATERIALIZE_INPUT,
                            RenderExecutionStepStatus.PENDING,
                            node.nodeId(),
                            node.artifactNodeType(),
                            null, null, null,
                            List.of(),
                            false, target,
                            "Materialize input: " + node.label(),
                            Map.of()));
                }

                // Step 2: PREPARE_PROVIDER_DOCUMENT
                String prepareStepId = RenderExecutionStepId.of(
                        planId.toString(), "PREPARE_PROVIDER_DOCUMENT", node.nodeId()).toString();
                ProviderExecutionDocumentDraft draft = draftIndex.get(node.nodeId());
                List<String> prepareDeps = new ArrayList<>();
                if (materializeStepId != null) {
                    prepareDeps.add(materializeStepId);
                }
                steps.add(new RenderExecutionStep(
                        prepareStepId,
                        RenderExecutionStepType.PREPARE_PROVIDER_DOCUMENT,
                        RenderExecutionStepStatus.PENDING,
                        node.nodeId(),
                        node.artifactNodeType(),
                        providerRef.providerName(),
                        providerRef,
                        draft,
                        List.copyOf(prepareDeps),
                        false, target,
                        "Prepare document: " + providerRef.providerName() + " / " + node.label(),
                        Map.of()));

                // Step 3: EXECUTE_PROVIDER (placeholder, executionReady=false)
                String executeStepId = RenderExecutionStepId.of(
                        planId.toString(), "EXECUTE_PROVIDER", node.nodeId()).toString();
                steps.add(new RenderExecutionStep(
                        executeStepId,
                        RenderExecutionStepType.EXECUTE_PROVIDER,
                        RenderExecutionStepStatus.PENDING,
                        node.nodeId(),
                        node.artifactNodeType(),
                        providerRef.providerName(),
                        providerRef,
                        null,
                        List.of(prepareStepId),
                        false, target,
                        "Execute: " + providerRef.providerName() + " / " + node.label(),
                        Map.of()));

                // Step 4-6: Output steps for FINAL_RENDER nodes
                if (node.artifactNodeType() == ArtifactNodeType.FINAL_RENDER) {
                    finalVerifyStepId = RenderExecutionStepId.of(
                            planId.toString(), "VERIFY_OUTPUT", node.nodeId()).toString();
                    steps.add(new RenderExecutionStep(
                            finalVerifyStepId,
                            RenderExecutionStepType.VERIFY_OUTPUT,
                            RenderExecutionStepStatus.PENDING,
                            node.nodeId(),
                            node.artifactNodeType(),
                            providerRef.providerName(),
                            providerRef, null,
                            List.of(executeStepId),
                            false, target,
                            "Verify output: " + node.label(),
                            Map.of()));

                    finalRegisterStepId = RenderExecutionStepId.of(
                            planId.toString(), "REGISTER_OUTPUT", node.nodeId()).toString();
                    steps.add(new RenderExecutionStep(
                            finalRegisterStepId,
                            RenderExecutionStepType.REGISTER_OUTPUT,
                            RenderExecutionStepStatus.PENDING,
                            node.nodeId(),
                            node.artifactNodeType(),
                            providerRef.providerName(),
                            providerRef, null,
                            List.of(finalVerifyStepId),
                            false, target,
                            "Register output: " + node.label(),
                            Map.of()));

                    finalLinkStepId = RenderExecutionStepId.of(
                            planId.toString(), "LINK_PRODUCT_DEPENDENCY", node.nodeId()).toString();
                    steps.add(new RenderExecutionStep(
                            finalLinkStepId,
                            RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY,
                            RenderExecutionStepStatus.PENDING,
                            node.nodeId(),
                            node.artifactNodeType(),
                            null, null, null,
                            List.of(finalRegisterStepId),
                            false, target,
                            "Link product dependency: " + node.label(),
                            Map.of()));
                }
            } else {
                // Failed/unbound node
                failureReasons.add(RenderExecutionPlanFailureReason.UNBOUND_CAPABILITY_NODE);
            }
        }

        // Step 7: FINALIZE_RENDER (depends on all output steps)
        List<String> finalizeDeps = new ArrayList<>();
        if (finalLinkStepId != null) {
            finalizeDeps.add(finalLinkStepId);
        }
        steps.add(new RenderExecutionStep(
                finalizeStepId,
                RenderExecutionStepType.FINALIZE_RENDER,
                RenderExecutionStepStatus.PENDING,
                null, null, null, null, null,
                List.copyOf(finalizeDeps),
                false,
                ExecutionEnvironmentTarget.LOCAL,
                "Finalize render",
                Map.of()));

        // Check for missing document drafts
        for (ProviderBindingNode node : bindingPlan.boundNodes()) {
            if (!draftIndex.containsKey(node.nodeId())
                    && node.artifactNodeType() != ArtifactNodeType.INPUT_MEDIA) {
                failureReasons.add(RenderExecutionPlanFailureReason.MISSING_DOCUMENT_DRAFT);
            }
        }

        boolean hasUnboundFailures = bindingPlan.hasFailures();
        boolean allStepsReady = steps.stream().allMatch(s -> s.executionReady());
        boolean executionReady = !hasUnboundFailures && failureReasons.isEmpty() && allStepsReady;

        log.info("Render execution plan compiled: planId={} mode={} steps={} failures={} execReady={}",
                planId, policy.mode(), steps.size(), failureReasons.size(), executionReady);

        return new RenderExecutionPlan(
                planId,
                bindingPlan.planId().toString(),
                bindingPlan.timelineId(),
                policy,
                ExecutionEnvironmentTarget.LOCAL,
                List.copyOf(steps),
                executionReady,
                List.copyOf(failureReasons));
    }

    /**
     * Resolve execution environment target based on provider and policy.
     */
    private ExecutionEnvironmentTarget resolveTarget(BoundProviderRef providerRef, ExecutionPolicy policy) {
        // v0: FFmpeg always gets LOCAL
        if ("ffmpeg".equalsIgnoreCase(providerRef.providerName())) {
            return ExecutionEnvironmentTarget.LOCAL;
        }
        // OpenCue-eligible providers get OPENCUE if policy allows
        if (policy.allowOpenCueSubmit()) {
            return ExecutionEnvironmentTarget.OPENCUE;
        }
        // Default to LOCAL for all v0 planning
        return ExecutionEnvironmentTarget.LOCAL;
    }
}
