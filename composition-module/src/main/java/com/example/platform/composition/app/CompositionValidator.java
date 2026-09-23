package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import java.util.*;

final class CompositionValidator {
    private CompositionValidator() {}
    static ValidationResult validate(TemplateWorkflow workflow, ProviderRegistryBoundary registry, Set<String> availableAssets, Set<String> entitlements) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, WorkflowStep> steps = new HashMap<>();
        Map<String,Integer> inputBindings = new HashMap<>();
        for (WorkflowStep step : workflow.steps()) {
            if (steps.put(step.id(), step) != null) issues.add(new ValidationIssue("DUPLICATE_STEP", "steps."+step.id(), "step id is duplicated", Severity.ERROR));
            var cap = registry.resolve(step.capabilityId(), step.capabilityVersion());
            if (cap.isEmpty()) issues.add(new ValidationIssue("MISSING_CAPABILITY", "steps."+step.id(), "capability is unavailable", Severity.ERROR));
            else if (cap.get().availability() != Availability.AVAILABLE) issues.add(new ValidationIssue("CAPABILITY_UNAVAILABLE", "steps."+step.id(), cap.get().summary(), Severity.ERROR));
            else if (Collections.disjoint(cap.get().executionModes(), workflow.executionModes())) issues.add(new ValidationIssue("UNSUPPORTED_EXECUTION_MODE", "steps."+step.id(), "requested execution mode is not supported by the capability", Severity.ERROR));
        }
        for (String cap : workflow.requiredCapabilities()) if (workflow.steps().stream().noneMatch(s -> s.capabilityId().equals(cap))) issues.add(new ValidationIssue("MISSING_CAPABILITY", "requiredCapabilities", cap, Severity.ERROR));
        for (String asset : workflow.requiredAssets()) if (!availableAssets.contains(asset)) issues.add(new ValidationIssue("MISSING_ASSET", "requiredAssets", asset, Severity.ERROR));
        for (int i=0;i<workflow.bindings().size();i++) {
            Binding b=workflow.bindings().get(i); String path="bindings["+i+"]";
            String target=b.toStep()+"/"+b.toInput(); inputBindings.merge(target,1,Integer::sum); if(inputBindings.get(target)>1) issues.add(new ValidationIssue("CARDINALITY_MISMATCH",path,"required input has multiple bindings",Severity.ERROR));
            if (!steps.containsKey(b.fromStep()) || !steps.containsKey(b.toStep())) issues.add(new ValidationIssue("INVALID_GRAPH_EDGE", path, "binding references an unknown step", Severity.ERROR));
            else {
                var result=compatible(steps.get(b.fromStep()), steps.get(b.toStep()), b, registry);
                if (!result.isEmpty()) issues.add(new ValidationIssue(result, path, "binding ports or contracts are incompatible", Severity.ERROR));
            }
        }
        if (hasCycle(workflow.steps(), workflow.bindings())) issues.add(new ValidationIssue("CIRCULAR_DEPENDENCY", "bindings", "workflow graph contains a cycle", Severity.ERROR));
        if (!workflow.steps().isEmpty()) {
            Set<String> incoming=new HashSet<>(), outgoing=new HashSet<>(); workflow.bindings().forEach(b->{incoming.add(b.toStep()); outgoing.add(b.fromStep());});
            Set<String> roots=new HashSet<>(); steps.keySet().forEach(id->{if(!incoming.contains(id)) roots.add(id);});
            Set<String> reachable=new HashSet<>(); roots.forEach(r->reach(r,workflow.bindings(),reachable));
            steps.keySet().stream().filter(id->!reachable.contains(id)).forEach(id->issues.add(new ValidationIssue("UNREACHABLE_NODE","steps."+id,"step is unreachable from a workflow entry",Severity.ERROR)));
            if (outgoing.size()==steps.size()) issues.add(new ValidationIssue("NO_TERMINAL_OUTPUT","steps","workflow must contain a terminal output step",Severity.ERROR));
        }
        if (workflow.executionModes().isEmpty()) issues.add(new ValidationIssue("UNSUPPORTED_EXECUTION_MODE", "executionModes", "at least one execution mode is required", Severity.ERROR));
        for (int i=0;i<workflow.parameters().size();i++) validateParameter(workflow.parameters().get(i),i,issues);
        return new ValidationResult(issues.stream().noneMatch(i -> i.severity() == Severity.ERROR), issues, UUID.randomUUID().toString());
    }
    static ValidationResult validateApplication(Application app, List<TemplateWorkflow> workflows, ProviderRegistryBoundary registry, Set<String> assets, Set<String> entitlements) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (app.workflowIds().isEmpty()) issues.add(new ValidationIssue("MISSING_WORKFLOW", "workflowIds", "application requires a workflow", Severity.ERROR));
        for (int i=0;i<app.workflowIds().size();i++) { final int index=i; String id=app.workflowIds().get(i); workflows.stream().filter(w -> w.id().equals(id)).findFirst().ifPresentOrElse(w -> {
            var r=validate(w, registry, assets, entitlements); r.issues().forEach(x->issues.add(new ValidationIssue(x.code(),"workflows["+index+"]"+(x.path().isBlank()?"":"."+x.path()),x.message(),x.severity())));
            if (Collections.disjoint(app.executionModes(),w.executionModes())) issues.add(new ValidationIssue("APPLICATION_EXECUTION_MODE_UNSUPPORTED","executionModes","application mode is unsupported by workflow "+id,Severity.ERROR));
            if (!versionCompatible(app.version(),w.version())) issues.add(new ValidationIssue("INCOMPATIBLE_VERSION_RANGE","workflowIds["+index+"]","application and workflow versions are incompatible",Severity.ERROR));
        }, () -> issues.add(new ValidationIssue("MISSING_WORKFLOW", "workflowIds["+index+"]", id, Severity.ERROR))); }
        for (String cap : app.requiredCapabilities()) if (registry.publicAvailability().stream().noneMatch(c -> c.capabilityId().equals(cap) && c.availability() == Availability.AVAILABLE)) issues.add(new ValidationIssue("MISSING_CAPABILITY", "requiredCapabilities", cap, Severity.ERROR));
        for (String a : app.requiredAssets()) if (!assets.contains(a)) issues.add(new ValidationIssue("MISSING_ASSET", "requiredAssets", a, Severity.ERROR));
        for (String e : app.entitlements()) if (!entitlements.contains(e)) issues.add(new ValidationIssue("MISSING_ENTITLEMENT", "entitlements", e, Severity.ERROR));
        return new ValidationResult(issues.stream().noneMatch(i -> i.severity()==Severity.ERROR), issues, UUID.randomUUID().toString());
    }
    private static String compatible(WorkflowStep a, WorkflowStep b, Binding binding, ProviderRegistryBoundary r) {
        var x=r.resolve(a.capabilityId(),a.capabilityVersion()); var y=r.resolve(b.capabilityId(),b.capabilityVersion());
        if (x.isEmpty() || y.isEmpty()) return "MISSING_CAPABILITY";
        if (!portMatches(binding.fromOutput(),x.get().output().name(),"output") || !portMatches(binding.toInput(),y.get().input().name(),"input")) return "INCOMPATIBLE_PORT";
        if (!x.get().output().name().equals(binding.type()) || !x.get().output().name().equals(y.get().input().name()) || !x.get().output().version().equals(y.get().input().version())) return "INCOMPATIBLE_CONTRACT";
        if (binding.type().endsWith("[]") && !binding.fromOutput().endsWith("[]")) return "CARDINALITY_MISMATCH";
        return "";
    }
    private static boolean portMatches(String actual,String expected,String kind){ return actual.equals(expected) || actual.equalsIgnoreCase(kind) || actual.equalsIgnoreCase(kind.equals("output")?"out":"in") || actual.equals(expected+"[]"); }
    private static void validateParameter(Parameter p,int i,List<ValidationIssue> issues){ String path="parameters["+i+"]"; Set<String> types=Set.of("string","integer","number","boolean","object","array"); if(!types.contains(p.type().toLowerCase())) issues.add(new ValidationIssue("INVALID_PARAMETER_SCHEMA",path+".type","unsupported parameter type",Severity.ERROR)); if(p.minimum()!=null&&p.maximum()!=null&&p.minimum()>p.maximum()) issues.add(new ValidationIssue("INVALID_PARAMETER_RANGE",path,"minimum exceeds maximum",Severity.ERROR)); if(p.required()&&p.defaultValue()==null) issues.add(new ValidationIssue("MISSING_PARAMETER_DEFAULT",path+".defaultValue","required parameter has no default",Severity.ERROR)); if(p.defaultValue()!=null&&!valueMatches(p.defaultValue(),p.type())) issues.add(new ValidationIssue("INVALID_PARAMETER_TYPE",path+".defaultValue","default value does not match parameter type",Severity.ERROR)); if(p.defaultValue() instanceof Number n && ((p.minimum()!=null&&n.doubleValue()<p.minimum())||(p.maximum()!=null&&n.doubleValue()>p.maximum()))) issues.add(new ValidationIssue("PARAMETER_OUT_OF_RANGE",path+".defaultValue","default value is outside range",Severity.ERROR)); }
    private static boolean valueMatches(Object v,String t){ return switch(t.toLowerCase()){case "string"->v instanceof String; case "integer"->v instanceof Integer||v instanceof Long; case "number"->v instanceof Number; case "boolean"->v instanceof Boolean; case "object"->v instanceof Map; case "array"->v instanceof Collection; default->false;}; }
    private static void reach(String node,List<Binding> bs,Set<String> seen){ if(!seen.add(node))return; bs.stream().filter(b->b.fromStep().equals(node)).forEach(b->reach(b.toStep(),bs,seen)); }
    private static boolean versionCompatible(String requested,String actual){ if(requested==null||actual==null)return false; if(requested.equals(actual))return true; if(requested.endsWith(".x"))return actual.startsWith(requested.substring(0,requested.length()-1)); if(requested.startsWith(">=")){try{return Double.parseDouble(actual)>=Double.parseDouble(requested.substring(2));}catch(NumberFormatException e){return false;}} return false; }
    private static boolean hasCycle(List<WorkflowStep> ss, List<Binding> bs) { Map<String,List<String>> g=new HashMap<>(); bs.forEach(b->g.computeIfAbsent(b.fromStep(),k->new ArrayList<>()).add(b.toStep())); Set<String> visiting=new HashSet<>(), done=new HashSet<>(); for (WorkflowStep s:ss) if (cycle(s.id(),g,visiting,done)) return true; return false; }
    private static boolean cycle(String n, Map<String,List<String>> g, Set<String> v, Set<String> d) { if (v.contains(n)) return true; if (d.contains(n)) return false; v.add(n); for(String x:g.getOrDefault(n,List.of())) if(cycle(x,g,v,d)) return true; v.remove(n); d.add(n); return false; }
}
