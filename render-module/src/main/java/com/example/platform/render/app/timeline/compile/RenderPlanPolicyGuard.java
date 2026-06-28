package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import com.example.platform.render.domain.timeline.compile.binding.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates a RenderExecutionPlan against safety and policy constraints
 * before any future execution.
 *
 * <p>v0: The guard marks plans as VALID_FOR_DRY_RUN, NOT_EXECUTABLE,
 * or FAILED_CLOSED. It does not enable execution.</p>
 *
 * <p>Internal only — not exposed in public APIs.</p>
 */
@Service
public class RenderPlanPolicyGuard {

    private static final Logger log = LoggerFactory.getLogger(RenderPlanPolicyGuard.class);

    /**
     * Evaluate a render execution plan against policy constraints.
     *
     * @param plan   the execution plan to evaluate
     * @param policy the execution policy
     * @return the policy result with violations
     */
    public RenderPlanPolicyResult evaluate(RenderExecutionPlan plan, ExecutionPolicy policy) {
        if (plan == null) {
            return RenderPlanPolicyResult.failedClosed(
                    List.of(), "Plan must not be null");
        }
        if (policy == null) {
            return RenderPlanPolicyResult.failedClosed(
                    List.of(), "Policy must not be null");
        }

        List<RenderPlanPolicyViolation> violations = new ArrayList<>();

        // Check 1: No unbound required capability node may become executable
        checkUnboundNodes(plan, violations);

        // Check 2: No non-production provider in PRODUCTION mode
        checkProductionProviders(plan, policy, violations);

        // Check 3: No provider with autoDispatch=false in automatic mode
        checkAutoDispatch(plan, policy, violations);

        // Check 4: No missing tool provider
        checkToolAvailability(plan, violations);

        // Check 5: No OpenFX without host
        checkOpenFxHost(plan, violations);

        // Check 6: No OpenCue unless enabled
        checkOpenCueTarget(plan, policy, violations);

        // Check 7: No raw command in steps
        checkNoRawCommand(plan, violations);

        // Check 8: No process environment in steps
        checkNoProcessEnvironment(plan, violations);

        // Check 9: No local path exposure
        checkNoLocalPaths(plan, violations);

        // Check 10: No storage internals exposure
        checkNoStorageInternals(plan, violations);

        // Check 11: Final output must have verification and registration
        checkFinalOutputSteps(plan, violations);

        // Check 12: Plan must be acyclic
        checkAcyclic(plan, violations);

        // Check 13: Step IDs must be deterministic
        checkDeterministicIds(plan, violations);

        // Check 14: Dependency graph validity
        checkDependencyGraph(plan, violations);

        // v0: All plans are not executable (no execution implemented)
        if (!violations.isEmpty()) {
            log.info("Policy guard found {} violations for plan {}",
                    violations.size(), plan.planId());
            return RenderPlanPolicyResult.notExecutable(violations,
                    "Plan has " + violations.size() + " policy violations");
        }

        // v0: even valid plans are dry-run only
        log.info("Policy guard passed for plan {} (dry-run only)", plan.planId());
        return RenderPlanPolicyResult.valid(
                "Plan is valid for dry-run planning (execution not implemented in v0)");
    }

    private void checkUnboundNodes(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.isProviderExecution() && step.providerRef() == null) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.UNBOUND_NODE_EXECUTABLE,
                        step.stepId(),
                        step.nodeId(),
                        "Provider execution step has no bound provider: " + step.stepId()));
            }
        }
    }

    private void checkProductionProviders(RenderExecutionPlan plan, ExecutionPolicy policy,
                                           List<RenderPlanPolicyViolation> violations) {
        if (!policy.isProductionMode()) return;
        for (RenderExecutionStep step : plan.steps()) {
            if (step.providerRef() != null
                    && step.providerRef().providerStatus() != ProviderStatus.PRODUCTION) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.NON_PRODUCTION_PROVIDER,
                        step.stepId(),
                        step.nodeId(),
                        "Non-production provider '" + step.providerRef().providerName()
                                + "' (status=" + step.providerRef().providerStatus()
                                + ") in PRODUCTION mode"));
            }
        }
    }

    private void checkAutoDispatch(RenderExecutionPlan plan, ExecutionPolicy policy,
                                    List<RenderPlanPolicyViolation> violations) {
        if (policy.isProductionMode()) {
            for (RenderExecutionStep step : plan.steps()) {
                if (step.providerRef() != null && !step.providerRef().autoDispatch()) {
                    violations.add(new RenderPlanPolicyViolation(
                            RenderPlanPolicyViolationType.AUTO_DISPATCH_DISABLED,
                            step.stepId(),
                            step.nodeId(),
                            "Provider '" + step.providerRef().providerName()
                                    + "' has autoDispatch=false in automatic mode"));
                }
            }
        }
    }

    private void checkToolAvailability(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.isProviderExecution()
                    && step.providerRef() != null
                    && !step.providerRef().toolAvailable()) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.TOOL_UNAVAILABLE,
                        step.stepId(),
                        step.nodeId(),
                        "Provider '" + step.providerRef().providerName()
                                + "' tool binary not available"));
            }
        }
    }

    private void checkOpenFxHost(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.providerName() != null
                    && "openfx".equalsIgnoreCase(step.providerName())) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.OPENFX_NO_HOST,
                        step.stepId(),
                        step.nodeId(),
                        "OpenFX capability requires an OFX host, not directly executable"));
            }
        }
    }

    private void checkOpenCueTarget(RenderExecutionPlan plan, ExecutionPolicy policy,
                                     List<RenderPlanPolicyViolation> violations) {
        if (policy.allowOpenCueSubmit()) return;
        for (RenderExecutionStep step : plan.steps()) {
            if (step.executionEnvironmentTarget() == ExecutionEnvironmentTarget.OPENCUE) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.OPENCUE_SUBMIT_DISABLED,
                        step.stepId(),
                        step.nodeId(),
                        "OpenCue target not allowed by policy"));
            }
        }
    }

    private void checkNoRawCommand(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.metadata() != null && step.metadata().containsKey("rawCommand")) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.RAW_COMMAND_EXPOSED,
                        step.stepId(),
                        step.nodeId(),
                        "Step contains raw command in metadata"));
            }
        }
    }

    private void checkNoProcessEnvironment(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.metadata() != null && step.metadata().containsKey("processEnvironment")) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.PROCESS_ENVIRONMENT_EXPOSED,
                        step.stepId(),
                        step.nodeId(),
                        "Step contains process environment in metadata"));
            }
        }
    }

    private void checkNoLocalPaths(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.metadata() != null) {
                String path = step.metadata().get("localPath");
                if (path != null && (path.startsWith("/") || path.startsWith("\\")
                        || path.matches("[A-Za-z]:\\\\.*"))) {
                    violations.add(new RenderPlanPolicyViolation(
                            RenderPlanPolicyViolationType.LOCAL_PATH_EXPOSED,
                            step.stepId(),
                            step.nodeId(),
                            "Step exposes local materialized path"));
                }
            }
        }
    }

    private void checkNoStorageInternals(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        List<String> storageKeys = List.of(
                "bucket", "objectKey", "rootPath", "relativePath",
                "materializedPath", "signedUrl", "storageReferenceId");
        for (RenderExecutionStep step : plan.steps()) {
            if (step.metadata() != null) {
                for (String key : storageKeys) {
                    if (step.metadata().containsKey(key)) {
                        violations.add(new RenderPlanPolicyViolation(
                                RenderPlanPolicyViolationType.STORAGE_INTERNALS_EXPOSED,
                                step.stepId(),
                                step.nodeId(),
                                "Step exposes storage internal: " + key));
                    }
                }
            }
        }
    }

    private void checkFinalOutputSteps(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        boolean hasVerify = plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.VERIFY_OUTPUT);
        boolean hasRegister = plan.steps().stream()
                .anyMatch(s -> s.type() == RenderExecutionStepType.REGISTER_OUTPUT);
        if (!hasVerify || !hasRegister) {
            violations.add(new RenderPlanPolicyViolation(
                    RenderPlanPolicyViolationType.OUTPUT_STEPS_MISSING,
                    null, null,
                    "Final output missing " + (!hasVerify ? "VERIFY_OUTPUT " : "")
                            + (!hasRegister ? "REGISTER_OUTPUT" : "")));
        }
    }

    private void checkAcyclic(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        // Simple cycle detection via DFS
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (RenderExecutionStep step : plan.steps()) {
            if (hasCycle(step.stepId(), plan, visited, inStack)) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.CYCLIC_DEPENDENCY,
                        step.stepId(),
                        step.nodeId(),
                        "Plan dependency graph contains a cycle involving step: " + step.stepId()));
                return; // One cycle violation is enough
            }
        }
    }

    private boolean hasCycle(String stepId, RenderExecutionPlan plan,
                             Set<String> visited, Set<String> inStack) {
        if (inStack.contains(stepId)) return true;
        if (visited.contains(stepId)) return false;
        visited.add(stepId);
        inStack.add(stepId);
        RenderExecutionStep step = plan.findStep(stepId);
        if (step != null && step.dependencies() != null) {
            for (String depId : step.dependencies()) {
                if (hasCycle(depId, plan, visited, inStack)) return true;
            }
        }
        inStack.remove(stepId);
        return false;
    }

    private void checkDeterministicIds(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        for (RenderExecutionStep step : plan.steps()) {
            if (step.stepId() == null || step.stepId().isBlank()) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.NON_DETERMINISTIC_STEP_ID,
                        step.stepId(),
                        step.nodeId(),
                        "Step has null or blank ID"));
            }
            if (step.stepId() != null && step.stepId().startsWith("random-")) {
                violations.add(new RenderPlanPolicyViolation(
                        RenderPlanPolicyViolationType.NON_DETERMINISTIC_STEP_ID,
                        step.stepId(),
                        step.nodeId(),
                        "Step ID appears non-deterministic: " + step.stepId()));
            }
        }
    }

    private void checkDependencyGraph(RenderExecutionPlan plan, List<RenderPlanPolicyViolation> violations) {
        Set<String> stepIds = new HashSet<>();
        for (RenderExecutionStep step : plan.steps()) {
            stepIds.add(step.stepId());
        }
        for (RenderExecutionStep step : plan.steps()) {
            if (step.dependencies() != null) {
                for (String dep : step.dependencies()) {
                    if (!stepIds.contains(dep)) {
                        violations.add(new RenderPlanPolicyViolation(
                                RenderPlanPolicyViolationType.INVALID_DEPENDENCY_GRAPH,
                                step.stepId(),
                                step.nodeId(),
                                "Step depends on non-existent step: " + dep));
                    }
                }
            }
        }
    }
}
