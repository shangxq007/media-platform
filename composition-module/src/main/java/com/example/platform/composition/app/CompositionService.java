package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CompositionService {
    private final ProviderRegistryBoundary registry;
    private final Map<String, TemplateWorkflow> workflows = new ConcurrentHashMap<>();
    private final Map<String, Application> applications = new ConcurrentHashMap<>();
    private final Map<String, ValidationResult> snapshots = new ConcurrentHashMap<>();
    public CompositionService(ProviderRegistryBoundary registry) { this.registry = registry; }
    public List<CapabilityAvailability> catalog() { return registry.publicAvailability(); }
    public TemplateWorkflow saveWorkflow(TemplateWorkflow value, String tenant) { ensureTenant(value.tenantId(), tenant); String key=tenant+":"+value.workspaceId()+":"+value.id(); TemplateWorkflow old=workflows.get(key); if(old!=null && value.revision()<old.revision()) throw new OptimisticConcurrencyException(); TemplateWorkflow saved=new TemplateWorkflow(value.id(), value.version(), value.name(), value.steps(), value.bindings(), value.parameters(), value.requiredCapabilities(), value.executionModes(), value.requiredAssets(), value.estimate(), value.reliability(), Lifecycle.DRAFT, tenant, value.workspaceId(), value.revision()+1); workflows.put(key,saved); return saved; }
    public Application saveApplication(Application value, String tenant) { ensureTenant(value.tenantId(), tenant); String key=tenant+":"+value.workspaceId()+":"+value.id(); Application old=applications.get(key); if(old!=null && value.revision()<old.revision()) throw new OptimisticConcurrencyException(); Application saved=new Application(value.id(),value.version(),value.displayName(),value.description(),value.input(),value.output(),value.requiredCapabilities(),value.workflowIds(),value.requiredAssets(),value.entitlements(),value.executionModes(),Lifecycle.DRAFT,tenant,value.workspaceId(),value.revision()+1); applications.put(key,saved); return saved; }
    public Optional<TemplateWorkflow> workflow(String tenant,String workspace,String id){return Optional.ofNullable(workflows.get(tenant+":"+workspace+":"+id));}
    public Optional<Application> application(String tenant,String workspace,String id){return Optional.ofNullable(applications.get(tenant+":"+workspace+":"+id));}
    public ValidationResult validateWorkflow(TemplateWorkflow w, String tenant, Set<String> assets){ensureTenant(w.tenantId(),tenant); var r=CompositionValidator.validate(w,registry,assets,Set.of()); snapshots.put(r.snapshotId(),r); return r;}
    public ValidationResult validateApplication(Application a,String tenant,Set<String> assets,Set<String> entitlements){ensureTenant(a.tenantId(),tenant); List<TemplateWorkflow> ws=a.workflowIds().stream().map(id->applicationWorkflows(tenant,a.workspaceId(),id)).flatMap(Optional::stream).toList(); var r=CompositionValidator.validateApplication(a,ws,registry,assets,entitlements); snapshots.put(r.snapshotId(),r); return r;}
    public TemplateWorkflow publishWorkflow(String tenant,String workspace,String id){var w=require(workflow(tenant,workspace,id)); var r=validateWorkflow(w,tenant,Set.of()); if(!r.ready()) throw new ValidationException(r); var p=new TemplateWorkflow(w.id(),w.version(),w.name(),w.steps(),w.bindings(),w.parameters(),w.requiredCapabilities(),w.executionModes(),w.requiredAssets(),w.estimate(),w.reliability(),Lifecycle.PUBLISHED,w.tenantId(),w.workspaceId(),w.revision()); workflows.put(tenant+":"+workspace+":"+id,p); return p;}
    public Application publishApplication(String tenant,String workspace,String id,Set<String> assets,Set<String> entitlements){var a=require(application(tenant,workspace,id)); var r=validateApplication(a,tenant,assets,entitlements); if(!r.ready()) throw new ValidationException(r); var p=new Application(a.id(),a.version(),a.displayName(),a.description(),a.input(),a.output(),a.requiredCapabilities(),a.workflowIds(),a.requiredAssets(),a.entitlements(),a.executionModes(),Lifecycle.PUBLISHED,a.tenantId(),a.workspaceId(),a.revision()); applications.put(tenant+":"+workspace+":"+id,p); return p;}
    private Optional<TemplateWorkflow> applicationWorkflows(String t,String w,String id){return workflow(t,w,id);}
    private static <T> T require(Optional<T> x){return x.orElseThrow(()->new NoSuchElementException("composition not found"));}
    private static void ensureTenant(String value,String current){if(!Objects.equals(value,current)) throw new ScopeViolationException();}
    public static class OptimisticConcurrencyException extends RuntimeException{}
    public static class ScopeViolationException extends RuntimeException{}
    public static class ValidationException extends RuntimeException{public final ValidationResult result; ValidationException(ValidationResult r){result=r;}}
}
