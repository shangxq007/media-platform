package com.example.platform.composition.domain;

import java.math.BigDecimal;
import java.util.*;

/** Provider-neutral composition foundation contracts. */
public final class CompositionModels {
    private CompositionModels() {}

    public enum ExecutionMode { SYNCHRONOUS, ASYNCHRONOUS, BATCH }
    public enum Availability { AVAILABLE, DEGRADED, UNAVAILABLE, INELIGIBLE }
    public enum Lifecycle { DRAFT, PUBLISHED }
    public enum Severity { ERROR, WARNING }

    public record ContractRef(String name, String version) {
        public ContractRef { require(name, "contract name"); require(version, "contract version"); }
    }
    public record CostEstimate(BigDecimal units, String unit, BigDecimal quotaUnits) {
        public CostEstimate { if (units == null || units.signum() < 0 || quotaUnits == null || quotaUnits.signum() < 0) throw new IllegalArgumentException("cost values must be non-negative"); require(unit, "cost unit"); }
    }
    public record Reliability(boolean cancellable, boolean retryable, int maxRetries) {
        public Reliability { if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be non-negative"); }
    }
    public record CapabilityAvailability(String capabilityId, String version, ContractRef input, ContractRef output,
                                         Set<String> assetTypes, Set<String> mediaTypes, Set<ExecutionMode> executionModes,
                                         Availability availability, String summary, CostEstimate estimate, Reliability reliability,
                                         Set<String> compatibleWorkflowTypes, Set<String> compatibleApplicationTypes) {
        public CapabilityAvailability {
            require(capabilityId, "capabilityId"); require(version, "version"); require(summary, "summary");
            if (input == null || output == null || availability == null || estimate == null || reliability == null) throw new IllegalArgumentException("incomplete capability metadata");
            assetTypes = immutable(assetTypes); mediaTypes = immutable(mediaTypes); executionModes = executionModes == null ? Set.of() : Set.copyOf(executionModes);
            compatibleWorkflowTypes = immutable(compatibleWorkflowTypes); compatibleApplicationTypes = immutable(compatibleApplicationTypes);
        }
    }
    public record Parameter(String name, String type, Object defaultValue, Double minimum, Double maximum, boolean required) {
        public Parameter { require(name, "parameter name"); require(type, "parameter type"); if (minimum != null && maximum != null && minimum > maximum) throw new IllegalArgumentException("parameter range is reversed"); }
    }
    public record Binding(String fromStep, String fromOutput, String toStep, String toInput, String type) {
        public Binding { require(fromStep, "fromStep"); require(fromOutput, "fromOutput"); require(toStep, "toStep"); require(toInput, "toInput"); require(type, "binding type"); }
    }
    public record WorkflowStep(String id, String capabilityId, String capabilityVersion, Map<String, Object> inputs, Set<String> requiredAssets, Set<String> alternatives) {
        public WorkflowStep { require(id, "step id"); require(capabilityId, "capabilityId"); require(capabilityVersion, "capabilityVersion"); inputs = inputs == null ? Map.of() : Map.copyOf(inputs); requiredAssets = immutable(requiredAssets); alternatives = immutable(alternatives); }
    }
    public record TemplateWorkflow(String id, String version, String name, List<WorkflowStep> steps, List<Binding> bindings,
                                   List<Parameter> parameters, Set<String> requiredCapabilities, Set<ExecutionMode> executionModes,
                                   Set<String> requiredAssets, CostEstimate estimate, Reliability reliability, Lifecycle lifecycle,
                                   String tenantId, String workspaceId, long revision) {
        public TemplateWorkflow { require(id, "workflow id"); require(version, "workflow version"); require(name, "workflow name"); require(tenantId, "tenantId"); require(workspaceId, "workspaceId");
            steps = steps == null ? List.of() : List.copyOf(steps); bindings = bindings == null ? List.of() : List.copyOf(bindings); parameters = parameters == null ? List.of() : List.copyOf(parameters);
            requiredCapabilities = immutable(requiredCapabilities); executionModes = executionModes == null ? Set.of() : Set.copyOf(executionModes); requiredAssets = immutable(requiredAssets); if (estimate == null || reliability == null || lifecycle == null) throw new IllegalArgumentException("incomplete workflow metadata"); }
    }
    public record Application(String id, String version, String displayName, String description, ContractRef input, ContractRef output,
                              Set<String> requiredCapabilities, List<String> workflowIds, Set<String> requiredAssets, Set<String> entitlements,
                              Set<ExecutionMode> executionModes, Lifecycle lifecycle, String tenantId, String workspaceId, long revision) {
        public Application { require(id, "application id"); require(version, "application version"); require(displayName, "displayName"); require(tenantId, "tenantId"); require(workspaceId, "workspaceId"); if (input == null || output == null || lifecycle == null) throw new IllegalArgumentException("incomplete application metadata"); requiredCapabilities = immutable(requiredCapabilities); workflowIds = workflowIds == null ? List.of() : List.copyOf(workflowIds); requiredAssets = immutable(requiredAssets); entitlements = immutable(entitlements); executionModes = executionModes == null ? Set.of() : Set.copyOf(executionModes); }
    }
    public record ValidationIssue(String code, String path, String message, Severity severity) {}
    public record ValidationResult(boolean ready, List<ValidationIssue> issues, String snapshotId) { public ValidationResult { issues = issues == null ? List.of() : List.copyOf(issues); } }
    static void require(String s, String n) { if (s == null || s.isBlank()) throw new IllegalArgumentException(n + " is required"); }
    static <T> Set<T> immutable(Set<T> v) { return v == null ? Set.of() : Set.copyOf(v); }
}
