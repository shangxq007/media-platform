package com.example.platform.render.domain.remotion;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates Remotion execution preflight conditions.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All evaluations return NOT_IMPLEMENTED or BLOCKED_BY_POLICY.</p>
 */
public class RemotionExecutionPolicyEvaluator {

    /**
     * Evaluate preflight for a potential Remotion execution.
     *
     * @param policy     execution policy
     * @param sandbox    sandbox policy
     * @param readiness  provider readiness
     * @param commandPlan command plan (null if not yet created)
     * @return preflight result
     */
    public RemotionExecutionPreflightResult evaluate(
            RemotionExecutionPolicy policy,
            RemotionSandboxPolicy sandbox,
            RemotionProviderReadiness readiness,
            RemotionExecutionCommandPlan commandPlan) {

        List<String> violations = new ArrayList<>();

        // Note: executionEnabled=false is not a violation — it's handled by READY_BUT_EXECUTION_DISABLED

        // 1. Runtime must be ready
        if (readiness != null && !readiness.executionReady()) {
            violations.add("Runtime executionReady=false");
        }

        // 3. Provider must not be production-eligible when production requested
        if (readiness != null && readiness.productionEligible()) {
            violations.add("Provider must not be production eligible");
        }

        // 4. Auto-dispatch must be disabled
        if (readiness != null && readiness.autoDispatch()) {
            violations.add("Auto-dispatch must be disabled");
        }

        // 5. Document generation must be ready
        if (readiness != null && !readiness.documentGenerationReady()) {
            violations.add("Document generation not ready");
        }

        // 6. Command plan validation (if provided)
        if (commandPlan != null) {
            violations.addAll(validateCommandPlan(commandPlan, policy));
        }

        // 7. Sandbox validation
        if (sandbox != null) {
            violations.addAll(validateSandbox(sandbox));
        }

        // If no violations but execution still disabled, return READY_BUT_EXECUTION_DISABLED
        if (violations.isEmpty() && !policy.executionEnabled()) {
            return new RemotionExecutionPreflightResult(
                    RemotionExecutionPreflightStatus.READY_BUT_EXECUTION_DISABLED,
                    List.of(), "All structural checks passed but execution remains disabled by policy", false);
        }

        // v0: NOT_IMPLEMENTED if execution would be enabled but runner does not exist
        if (violations.isEmpty()) {
            return new RemotionExecutionPreflightResult(
                    RemotionExecutionPreflightStatus.NOT_IMPLEMENTED,
                    List.of(), "Execution not implemented in v0", false);
        }

        // Determine primary status
        RemotionExecutionPreflightStatus status = determineStatus(policy, readiness, commandPlan, violations);

        return new RemotionExecutionPreflightResult(
                status, List.copyOf(violations),
                "Preflight blocked: " + violations.size() + " violation(s)", false);
    }

    private List<String> validateCommandPlan(RemotionExecutionCommandPlan plan,
                                              RemotionExecutionPolicy policy) {
        List<String> issues = new ArrayList<>();

        // Structural validation
        issues.addAll(plan.structuralIssues());

        // Reject shell-like commands
        if (plan.arguments() != null) {
            for (String arg : plan.arguments()) {
                if (arg != null && (arg.contains(";") || arg.contains("&&") || arg.contains("|"))) {
                    issues.add("Shell metacharacters in arguments: " + arg);
                }
            }
        }

        // Reject npx remotion
        if (plan.executableRef() != null && plan.executableRef().contains("remotion")) {
            // Only allowed if it's a trusted internal reference
            if (!plan.executableRef().startsWith("internal://")) {
                issues.add("Executable ref must be trusted internal reference");
            }
        }

        // Reject network when not allowed
        if (plan.networkPolicy() == RemotionExecutionNetworkPolicy.ALLOWED && !policy.networkAllowed()) {
            issues.add("Network access not allowed by policy");
        }

        // Reject signed URLs in arguments
        if (plan.arguments() != null) {
            for (String arg : plan.arguments()) {
                if (arg != null && (arg.contains("X-Amz-Signature") || arg.contains("signedUrl"))) {
                    issues.add("Signed URL in arguments");
                }
            }
        }

        // Reject bucket/objectKey in arguments
        if (plan.arguments() != null) {
            for (String arg : plan.arguments()) {
                if (arg != null && (arg.contains("bucket=") || arg.contains("objectKey="))) {
                    issues.add("Storage internals in arguments");
                }
            }
        }

        return issues;
    }

    private List<String> validateSandbox(RemotionSandboxPolicy sandbox) {
        // Sandbox constraints are always satisfied in v0 since we define them
        // Actual enforcement would happen in future runner
        return List.of();
    }

    private RemotionExecutionPreflightStatus determineStatus(
            RemotionExecutionPolicy policy,
            RemotionProviderReadiness readiness,
            RemotionExecutionCommandPlan commandPlan,
            List<String> violations) {

        if (!policy.executionEnabled()) {
            return RemotionExecutionPreflightStatus.BLOCKED_BY_POLICY;
        }
        if (readiness != null && !readiness.executionReady()) {
            return RemotionExecutionPreflightStatus.BLOCKED_BY_RUNTIME;
        }
        if (commandPlan != null && !commandPlan.structuralIssues().isEmpty()) {
            return RemotionExecutionPreflightStatus.BLOCKED_BY_UNSAFE_COMMAND;
        }
        return RemotionExecutionPreflightStatus.BLOCKED_BY_POLICY;
    }
}
