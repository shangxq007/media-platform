package com.example.platform.shared.capability.flow;

import com.example.platform.shared.capability.AutomationFlow;
import com.example.platform.shared.capability.SystemAction;
import com.example.platform.shared.capability.execution.*;
import com.example.platform.shared.capability.registry.SystemActionRegistry;
import com.example.platform.shared.capability.validation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dry-run executor for automation flows.
 *
 * <p>AutomationFlowDryRunExecutor validates flows and produces execution traces
 * without executing real actions. This is for validation/explain-plan only.</p>
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Runtime execution is not implemented.</p>
 */
public class AutomationFlowDryRunExecutor {

    private final AutomationFlowValidator flowValidator;
    private final SystemActionExecutor actionExecutor;
    private final SystemActionRegistry actionRegistry;

    /**
     * Create a dry-run executor with the given dependencies.
     *
     * @param flowValidator the flow validator
     * @param actionExecutor the action executor
     * @param actionRegistry the action registry
     */
    public AutomationFlowDryRunExecutor(
        AutomationFlowValidator flowValidator,
        SystemActionExecutor actionExecutor,
        SystemActionRegistry actionRegistry
    ) {
        this.flowValidator = Objects.requireNonNull(flowValidator, "flowValidator must not be null");
        this.actionExecutor = Objects.requireNonNull(actionExecutor, "actionExecutor must not be null");
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry must not be null");
    }

    /**
     * Execute a dry-run of an automation flow.
     *
     * @param request the dry-run request
     * @return the dry-run result
     */
    public AutomationFlowDryRunResult execute(AutomationFlowDryRunRequest request) {
        if (request == null) {
            Instant now = Instant.now();
            return new AutomationFlowDryRunResult(
                AutomationFlowDryRunStatus.FAILED,
                null,
                List.of(),
                now,
                now,
                true
            );
        }

        Instant startedAt = Instant.now();

        // Step 1: Validate the flow
        AutomationFlowValidationResult validationResult = flowValidator.validate(request.flow());

        // Step 2: If validation has errors, return VALIDATION_FAILED
        if (validationResult.hasErrors()) {
            return AutomationFlowDryRunResult.validationFailed(validationResult);
        }

        // Step 3: Process nodes in deterministic order
        List<AutomationNodeDryRunResult> nodeResults = processNodes(request);

        // Step 4: Determine overall status
        boolean hasNotImplemented = nodeResults.stream()
            .anyMatch(r -> r.status() == AutomationNodeDryRunStatus.NOT_IMPLEMENTED);

        if (hasNotImplemented) {
            return AutomationFlowDryRunResult.partiallySupported(validationResult, nodeResults, startedAt);
        }

        return AutomationFlowDryRunResult.succeeded(validationResult, nodeResults, startedAt);
    }

    /**
     * Process nodes in deterministic order.
     */
    private List<AutomationNodeDryRunResult> processNodes(AutomationFlowDryRunRequest request) {
        List<AutomationNodeDryRunResult> results = new ArrayList<>();

        // Process nodes in insertion order (stable)
        for (AutomationFlow.FlowNode node : request.flow().nodes()) {
            AutomationNodeDryRunResult result = processNode(node, request);
            results.add(result);
        }

        return results;
    }

    /**
     * Process a single node.
     */
    private AutomationNodeDryRunResult processNode(AutomationFlow.FlowNode node, AutomationFlowDryRunRequest request) {
        switch (node.nodeType()) {
            case ACTION:
                return processActionNode(node, request);
            case EXTENSION_POINT:
                return processExtensionPointNode(node);
            case HOOK:
                return processHookNode(node);
            case NOTIFICATION:
                return processNotificationNode(node, request);
            case WEBHOOK:
                return processWebhookNode(node, request);
            case CONDITION:
                return processConditionNode(node);
            case APPROVAL:
                return processApprovalNode(node);
            case DELAY:
                return processDelayNode(node);
            default:
                return AutomationNodeDryRunResult.notImplemented(
                    node.nodeId(),
                    node.nodeType(),
                    node.capabilityKey(),
                    "Unknown node type: " + node.nodeType()
                );
        }
    }

    /**
     * Process an ACTION node through the validating executor.
     */
    private AutomationNodeDryRunResult processActionNode(AutomationFlow.FlowNode node, AutomationFlowDryRunRequest request) {
        // Create execution request with dryRun=true
        SystemActionExecutionRequest executionRequest = new SystemActionExecutionRequest(
            node.capabilityKey(),
            node.capabilityVersion(),
            request.input(),
            request.options(),
            request.context().idempotencyKey()
        );

        // Execute with dryRun context
        SystemActionExecutionResult executionResult = actionExecutor.execute(request.context(), executionRequest);

        // Map execution result to node result
        switch (executionResult.status()) {
            case DRY_RUN_SUCCEEDED:
                return AutomationNodeDryRunResult.dryRunSucceeded(
                    node.nodeId(),
                    node.nodeType(),
                    node.capabilityKey()
                );
            case VALIDATION_FAILED:
                return AutomationNodeDryRunResult.validationFailed(
                    node.nodeId(),
                    node.nodeType(),
                    node.capabilityKey(),
                    executionResult.errorCode(),
                    "Action validation failed: " + executionResult.errorCode()
                );
            case NOT_IMPLEMENTED:
                return AutomationNodeDryRunResult.notImplemented(
                    node.nodeId(),
                    node.nodeType(),
                    node.capabilityKey(),
                    "Action execution not implemented"
                );
            default:
                return AutomationNodeDryRunResult.failed(
                    node.nodeId(),
                    node.nodeType(),
                    node.capabilityKey(),
                    executionResult.errorCode(),
                    "Action execution failed: " + executionResult.status()
                );
        }
    }

    /**
     * Process an EXTENSION_POINT node (not implemented).
     */
    private AutomationNodeDryRunResult processExtensionPointNode(AutomationFlow.FlowNode node) {
        return AutomationNodeDryRunResult.notImplemented(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Extension point execution not implemented"
        );
    }

    /**
     * Process a HOOK node (not implemented).
     */
    private AutomationNodeDryRunResult processHookNode(AutomationFlow.FlowNode node) {
        return AutomationNodeDryRunResult.notImplemented(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Hook execution not implemented"
        );
    }

    /**
     * Process a NOTIFICATION node (maps to notification.send action if registered).
     */
    private AutomationNodeDryRunResult processNotificationNode(AutomationFlow.FlowNode node, AutomationFlowDryRunRequest request) {
        // Try to map to notification.send action
        String actionKey = "notification.send";
        Optional<SystemAction> action = actionRegistry.findByKey(actionKey);

        if (action.isPresent()) {
            // Create a modified node with the action key
            AutomationFlow.FlowNode actionNode = new AutomationFlow.FlowNode(
                node.nodeId(),
                AutomationFlow.NodeType.ACTION,
                actionKey,
                "1.0.0",
                node.config(),
                node.requiredPermissions(),
                node.errorPolicy()
            );
            return processActionNode(actionNode, request);
        }

        return AutomationNodeDryRunResult.notImplemented(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Notification action not registered"
        );
    }

    /**
     * Process a WEBHOOK node (maps to webhook.send action if registered).
     */
    private AutomationNodeDryRunResult processWebhookNode(AutomationFlow.FlowNode node, AutomationFlowDryRunRequest request) {
        // Try to map to webhook.send action
        String actionKey = "webhook.send";
        Optional<SystemAction> action = actionRegistry.findByKey(actionKey);

        if (action.isPresent()) {
            // Create a modified node with the action key
            AutomationFlow.FlowNode actionNode = new AutomationFlow.FlowNode(
                node.nodeId(),
                AutomationFlow.NodeType.ACTION,
                actionKey,
                "1.0.0",
                node.config(),
                node.requiredPermissions(),
                node.errorPolicy()
            );
            return processActionNode(actionNode, request);
        }

        return AutomationNodeDryRunResult.notImplemented(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Webhook action not registered"
        );
    }

    /**
     * Process a CONDITION node (skipped).
     */
    private AutomationNodeDryRunResult processConditionNode(AutomationFlow.FlowNode node) {
        return AutomationNodeDryRunResult.skipped(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Condition evaluation skipped in dry-run"
        );
    }

    /**
     * Process an APPROVAL node (skipped).
     */
    private AutomationNodeDryRunResult processApprovalNode(AutomationFlow.FlowNode node) {
        return AutomationNodeDryRunResult.skipped(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Approval step skipped in dry-run"
        );
    }

    /**
     * Process a DELAY node (skipped).
     */
    private AutomationNodeDryRunResult processDelayNode(AutomationFlow.FlowNode node) {
        return AutomationNodeDryRunResult.skipped(
            node.nodeId(),
            node.nodeType(),
            node.capabilityKey(),
            "Delay skipped in dry-run"
        );
    }
}
