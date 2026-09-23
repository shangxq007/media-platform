package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import java.util.*;

final class CompositionValidator {
    private CompositionValidator() {}
    static ValidationResult validate(TemplateWorkflow workflow, ProviderRegistryBoundary registry, Set<String> availableAssets, Set<String> entitlements) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, WorkflowStep> steps = new HashMap<>();
        for (WorkflowStep step : workflow.steps()) {
            if (steps.put(step.id(), step) != null) issues.add(new ValidationIssue("DUPLICATE_STEP", "steps."+step.id(), "step id is duplicated", Severity.ERROR));
            var cap = registry.resolve(step.capabilityId(), step.capabilityVersion());
            if (cap.isEmpty()) issues.add(new ValidationIssue("MISSING_CAPABILITY", "steps."+step.id(), "capability is unavailable", Severity.ERROR));
            else if (cap.get().availability() != Availability.AVAILABLE) issues.add(new ValidationIssue("CAPABILITY_UNAVAILABLE", "steps."+step.id(), cap.get().summary(), Severity.ERROR));
            else if (Collections.disjoint(cap.get().executionModes(), workflow.executionModes())) issues.add(new ValidationIssue("UNSUPPORTED_EXECUTION_MODE", "steps."+step.id(), "requested execution mode is not supported by the capability", Severity.ERROR));
        }
        for (String cap : workflow.requiredCapabilities()) if (workflow.steps().stream().noneMatch(s -> s.capabilityId().equals(cap))) issues.add(new ValidationIssue("MISSING_CAPABILITY", "requiredCapabilities", cap, Severity.ERROR));
        for (String asset : workflow.requiredAssets()) if (!availableAssets.contains(asset)) issues.add(new ValidationIssue("MISSING_ASSET", "requiredAssets", asset, Severity.ERROR));
        for (Binding b : workflow.bindings()) {
            if (!steps.containsKey(b.fromStep()) || !steps.containsKey(b.toStep())) issues.add(new ValidationIssue("INVALID_BINDING", "bindings", "binding references an unknown step", Severity.ERROR));
            else if (!compatible(steps.get(b.fromStep()), steps.get(b.toStep()), b.type(), registry)) issues.add(new ValidationIssue("INCOMPATIBLE_CONTRACT", "bindings", "input/output contracts or ports are incompatible", Severity.ERROR));
        }
        if (hasCycle(workflow.steps(), workflow.bindings())) issues.add(new ValidationIssue("CIRCULAR_DEPENDENCY", "bindings", "workflow graph contains a cycle", Severity.ERROR));
        if (workflow.executionModes().isEmpty()) issues.add(new ValidationIssue("UNSUPPORTED_EXECUTION_MODE", "executionModes", "at least one execution mode is required", Severity.ERROR));
        for (Parameter p : workflow.parameters()) if (p.minimum() != null && p.maximum() != null && p.minimum() > p.maximum()) issues.add(new ValidationIssue("INVALID_PARAMETER_RANGE", "parameters."+p.name(), "minimum exceeds maximum", Severity.ERROR));
        return new ValidationResult(issues.stream().noneMatch(i -> i.severity() == Severity.ERROR), issues, UUID.randomUUID().toString());
    }
    static ValidationResult validateApplication(Application app, List<TemplateWorkflow> workflows, ProviderRegistryBoundary registry, Set<String> assets, Set<String> entitlements) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (app.workflowIds().isEmpty()) issues.add(new ValidationIssue("MISSING_WORKFLOW", "workflowIds", "application requires a workflow", Severity.ERROR));
        for (String id : app.workflowIds()) workflows.stream().filter(w -> w.id().equals(id)).findFirst().ifPresentOrElse(w -> { var r=validate(w, registry, assets, entitlements); issues.addAll(r.issues()); }, () -> issues.add(new ValidationIssue("MISSING_WORKFLOW", "workflowIds", id, Severity.ERROR)));
        for (String cap : app.requiredCapabilities()) if (registry.publicAvailability().stream().noneMatch(c -> c.capabilityId().equals(cap) && c.availability() == Availability.AVAILABLE)) issues.add(new ValidationIssue("MISSING_CAPABILITY", "requiredCapabilities", cap, Severity.ERROR));
        for (String a : app.requiredAssets()) if (!assets.contains(a)) issues.add(new ValidationIssue("MISSING_ASSET", "requiredAssets", a, Severity.ERROR));
        for (String e : app.entitlements()) if (!entitlements.contains(e)) issues.add(new ValidationIssue("MISSING_ENTITLEMENT", "entitlements", e, Severity.ERROR));
        return new ValidationResult(issues.stream().noneMatch(i -> i.severity()==Severity.ERROR), issues, UUID.randomUUID().toString());
    }
    private static boolean compatible(WorkflowStep a, WorkflowStep b, String type, ProviderRegistryBoundary r) { var x=r.resolve(a.capabilityId(),a.capabilityVersion()); var y=r.resolve(b.capabilityId(),b.capabilityVersion()); return x.isPresent() && y.isPresent() && x.get().output().name().equals(y.get().input().name()) && x.get().output().version().equals(y.get().input().version()) && x.get().output().name().equals(type); }
    private static boolean hasCycle(List<WorkflowStep> ss, List<Binding> bs) { Map<String,List<String>> g=new HashMap<>(); bs.forEach(b->g.computeIfAbsent(b.fromStep(),k->new ArrayList<>()).add(b.toStep())); Set<String> visiting=new HashSet<>(), done=new HashSet<>(); for (WorkflowStep s:ss) if (cycle(s.id(),g,visiting,done)) return true; return false; }
    private static boolean cycle(String n, Map<String,List<String>> g, Set<String> v, Set<String> d) { if (v.contains(n)) return true; if (d.contains(n)) return false; v.add(n); for(String x:g.getOrDefault(n,List.of())) if(cycle(x,g,v,d)) return true; v.remove(n); d.add(n); return false; }
}
