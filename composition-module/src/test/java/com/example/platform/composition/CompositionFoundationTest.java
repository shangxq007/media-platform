package com.example.platform.composition;

import com.example.platform.composition.app.*;
import com.example.platform.composition.app.CompositionValidator;
import com.example.platform.composition.domain.CompositionModels.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositionFoundationTest {
    private final ProviderRegistryBoundary registry = new TestProviderRegistry();
    private final CompositionAccess access = org.mockito.Mockito.mock(CompositionAccess.class);
    private final CompositionRepository repository = org.mockito.Mockito.mock(CompositionRepository.class);
    private final CompositionResourceAuthority resources = org.mockito.Mockito.mock(CompositionResourceAuthority.class);
    private final CompositionService service = new CompositionService(registry, repository, access, resources);
    { org.mockito.Mockito.when(access.resolve("w1")).thenReturn(new CompositionAccess.Scope("t1","w1","actor")); org.mockito.Mockito.when(resources.resolve(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.anySet(),org.mockito.ArgumentMatchers.any(BigDecimal.class))).thenReturn(new CompositionResourceAuthority.ResourceCheck(Set.of(),Set.of(),List.of())); }
    private TemplateWorkflow workflow(String id, List<Binding> bindings) { return new TemplateWorkflow(id,"1.0","Test",List.of(new WorkflowStep("a","media.thumbnail","1.0",Map.of(),Set.of(),Set.of()),new WorkflowStep("b","media.watermark","1.0",Map.of(),Set.of(),Set.of())),bindings,List.of(),Set.of("media.thumbnail"),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of(),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0); }
    @Test void catalogIsProviderNeutralAndNonEmpty(){assertFalse(registry.publicAvailability().isEmpty()); assertTrue(registry.publicAvailability().stream().noneMatch(x->x.toString().toLowerCase().contains("provider")));}
    @Test void rejectsUnknownCapabilityAndCycle(){var bad=workflow("w",List.of(new Binding("a","out","missing","in","ImageAsset"),new Binding("b","out","a","in","ImageAsset"),new Binding("a","out","b","in","ImageAsset"))); var r=service.validateWorkflow(bad,"t1",Set.of()); assertFalse(r.ready()); assertTrue(r.issues().stream().anyMatch(i->i.code().equals("INVALID_BINDING")||i.code().equals("CIRCULAR_DEPENDENCY")));}
    @Test void repositoryWritesAreScopedAndVersioned(){var w=workflow("w",List.of()); service.saveWorkflow(w,"t1"); org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.eq(CompositionRepository.Kind.WORKFLOW),org.mockito.ArgumentMatchers.eq("t1"),org.mockito.ArgumentMatchers.eq("w1"),org.mockito.ArgumentMatchers.eq("w"),org.mockito.ArgumentMatchers.eq("1.0"),org.mockito.ArgumentMatchers.eq(0L),org.mockito.ArgumentMatchers.any());}
    @Test void rejectsMissingAssetsAndEntitlements(){var w=new TemplateWorkflow("w","1.0","T",List.of(),List.of(),List.of(),Set.of(),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of("video"),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0); assertTrue(service.validateWorkflow(w,"t1",Set.of()).issues().stream().anyMatch(i->i.code().equals("MISSING_ASSET"))); var a=new Application("a","1.0","A","",new ContractRef("x","1"),new ContractRef("y","1"),Set.of(),List.of(),Set.of(),Set.of("entitlement.compose"),Set.of(ExecutionMode.ASYNCHRONOUS),Lifecycle.DRAFT,"t1","w1",0); assertTrue(service.validateApplication(a,"t1",Set.of(),Set.of()).issues().stream().anyMatch(i->i.code().equals("MISSING_ENTITLEMENT")));}
    @Test void rejectsOutputlessAndDisconnectedWorkflowsWithStructuredIssues(){
        var a=new WorkflowStep("a","media.thumbnail","1.0",Map.of(),Set.of(),Set.of());
        var b=new WorkflowStep("b","media.watermark","1.0",Map.of(),Set.of(),Set.of());
        var w=new TemplateWorkflow("w","1.0","T",List.of(a,b),List.of(),List.of(),Set.of("media.thumbnail"),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of(),List.of(),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0);
        var result=service.validateWorkflow(w,"t1",Set.of());
        assertFalse(result.ready());
        assertTrue(result.issues().stream().anyMatch(i->i.code().equals("MISSING_WORKFLOW_OUTPUT")));
        assertTrue(result.issues().stream().anyMatch(i->i.objectType().equals("workflow") && i.objectId().equals("w") && i.location().equals("outputs")));
    }
    @Test void rejectsMalformedVersionRanges(){
        assertEquals("MALFORMED_VERSION_RANGE", CompositionValidator.versionCompatibility("", "1.0"));
        assertEquals("MALFORMED_VERSION_RANGE", CompositionValidator.versionCompatibility("wat", "1.0"));
        assertEquals("INCOMPATIBLE_VERSION_RANGE", CompositionValidator.versionCompatibility(">=2.0 <3.0", "1.0"));
        assertEquals("OK", CompositionValidator.versionCompatibility(">=1.0 <2.0", "1.5"));
    }
    private TemplateWorkflow single(boolean withEntry, boolean withOutput) {
        var step = new WorkflowStep("source","media.thumbnail","1.0",Map.of(),Set.of(),Set.of());
        var entry = withEntry ? new WorkflowEntry("input", new ContractRef("MediaAsset","1"), "source", "input") : null;
        List<WorkflowOutput> output = withOutput ? List.of(new WorkflowOutput("result","ImageAsset","source","output")) : List.of();
        return new TemplateWorkflow("single","1.0","Single",List.of(step),List.of(),List.of(),Set.of("media.thumbnail"),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of(),output,entry,new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0);
    }
    @Test void singleNodeWithoutEntryIsRejected() { var r=service.validateWorkflow(single(false,true),"t1",Set.of()); assertFalse(r.ready()); assertTrue(r.issues().stream().anyMatch(i->i.code().equals("MISSING_WORKFLOW_ENTRY") && i.path().equals("entry"))); }
    @Test void singleNodeWithEntryAndOutputIsAccepted() { var r=service.validateWorkflow(single(true,true),"t1",Set.of()); assertTrue(r.ready(), r.issues().toString()); }
    @Test void invalidEntryTargetIsRejected() { var w=single(true,true); var bad=new TemplateWorkflow(w.id(),w.version(),w.name(),w.steps(),w.bindings(),w.parameters(),w.requiredCapabilities(),w.executionModes(),w.requiredAssets(),w.outputs(),new WorkflowEntry("input",new ContractRef("MediaAsset","1"),"missing","input"),w.estimate(),w.reliability(),w.lifecycle(),w.tenantId(),w.workspaceId(),w.revision()); var r=service.validateWorkflow(bad,"t1",Set.of()); assertTrue(r.issues().stream().anyMatch(i->i.code().equals("INVALID_ENTRY_NODE"))); }
    @Test void missingOutputStillRejectedWithValidEntry() { var r=service.validateWorkflow(single(true,false),"t1",Set.of()); assertTrue(r.issues().stream().anyMatch(i->i.code().equals("MISSING_WORKFLOW_OUTPUT"))); }
    @Test void twoStepChainWithExplicitEntryRemainsValid() { var a=new WorkflowStep("a","media.thumbnail","1.0",Map.of(),Set.of(),Set.of()); var b=new WorkflowStep("b","media.watermark","1.0",Map.of(),Set.of(),Set.of()); var w=new TemplateWorkflow("chain","1.0","Chain",List.of(a,b),List.of(new Binding("a","output","b","input","ImageAsset")),List.of(),Set.of("media.thumbnail","media.watermark"),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of(),List.of(new WorkflowOutput("result","ImageAsset","b","output")),new WorkflowEntry("input",new ContractRef("MediaAsset","1"),"a","input"),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0); var r=service.validateWorkflow(w,"t1",Set.of()); assertTrue(r.ready(),r.issues().toString()); }

    @Test void providerExecutionOutputMayBeAWorkflowTerminalButCannotFeedMediaAssetSilently() {
        var providerRegistry = new ProviderRegistryBoundary() {
            private final List<CapabilityAvailability> values = List.of(
                    new CapabilityAvailability("media.transcode", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("ProviderExecutionOutput", "1"), Set.of(), Set.of(), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "test", new CostEstimate(BigDecimal.ONE, "u", BigDecimal.ONE), new Reliability(true, true, 1), Set.of(), Set.of()),
                    new CapabilityAvailability("media.consume", "1.0", new ContractRef("Artifact", "1"), new ContractRef("Artifact", "1"), Set.of(), Set.of(), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "test", new CostEstimate(BigDecimal.ONE, "u", BigDecimal.ONE), new Reliability(true, true, 1), Set.of(), Set.of()));
            public List<CapabilityAvailability> publicAvailability() { return values; }
            public Optional<CapabilityAvailability> resolve(String id, String version) { return values.stream().filter(v -> v.capabilityId().equals(id) && v.version().equals(version)).findFirst(); }
        };
        var source = new WorkflowStep("source", "media.transcode", "1.0", Map.of(), Set.of(), Set.of());
        var consumer = new WorkflowStep("consumer", "media.consume", "1.0", Map.of(), Set.of(), Set.of());
        var terminal = new TemplateWorkflow("terminal", "1.0", "Terminal", List.of(source), List.of(), List.of(), Set.of("media.transcode"), Set.of(ExecutionMode.ASYNCHRONOUS), Set.of(), List.of(new WorkflowOutput("result", "ProviderExecutionOutput", "source", "output")), new WorkflowEntry("input", new ContractRef("MediaAsset", "1"), "source", "input"), new CostEstimate(BigDecimal.ONE, "u", BigDecimal.ONE), new Reliability(true, true, 1), Lifecycle.DRAFT, "t1", "w1", 0);
        var providerService = new CompositionService(providerRegistry, repository, access, resources);
        assertTrue(providerService.validateWorkflow(terminal, "t1", Set.of()).ready());
        var rawToMedia = new TemplateWorkflow("raw", "1.0", "Raw", List.of(source, consumer), List.of(new Binding("source", "output", "consumer", "input", "Artifact")), List.of(), Set.of("media.transcode", "media.consume"), Set.of(ExecutionMode.ASYNCHRONOUS), Set.of(), List.of(new WorkflowOutput("result", "Artifact", "consumer", "output")), new WorkflowEntry("input", new ContractRef("MediaAsset", "1"), "source", "input"), new CostEstimate(BigDecimal.ONE, "u", BigDecimal.ONE), new Reliability(true, true, 1), Lifecycle.DRAFT, "t1", "w1", 0);
        assertTrue(providerService.validateWorkflow(rawToMedia, "t1", Set.of()).issues().stream().anyMatch(i -> i.code().equals("MATERIALIZATION_REQUIRED")));
    }

}
