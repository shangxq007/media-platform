package com.example.platform.composition;

import com.example.platform.composition.app.*;
import com.example.platform.composition.domain.CompositionModels.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositionFoundationTest {
    private final ProviderRegistryBoundary registry = new TestProviderRegistry();
    private final CompositionAccess access = org.mockito.Mockito.mock(CompositionAccess.class);
    private final CompositionRepository repository = org.mockito.Mockito.mock(CompositionRepository.class);
    private final CompositionService service = new CompositionService(registry, repository, access);
    { org.mockito.Mockito.when(access.resolve("w1")).thenReturn(new CompositionAccess.Scope("t1","w1","actor")); }
    private TemplateWorkflow workflow(String id, List<Binding> bindings) { return new TemplateWorkflow(id,"1.0","Test",List.of(new WorkflowStep("a","media.thumbnail","1.0",Map.of(),Set.of(),Set.of()),new WorkflowStep("b","media.thumbnail","1.0",Map.of(),Set.of(),Set.of())),bindings,List.of(),Set.of("media.thumbnail"),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of(),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0); }
    @Test void catalogIsProviderNeutralAndNonEmpty(){assertFalse(registry.publicAvailability().isEmpty()); assertTrue(registry.publicAvailability().stream().noneMatch(x->x.toString().toLowerCase().contains("provider")));}
    @Test void rejectsUnknownCapabilityAndCycle(){var bad=workflow("w",List.of(new Binding("a","out","missing","in","ImageAsset"),new Binding("b","out","a","in","ImageAsset"),new Binding("a","out","b","in","ImageAsset"))); var r=service.validateWorkflow(bad,"t1",Set.of()); assertFalse(r.ready()); assertTrue(r.issues().stream().anyMatch(i->i.code().equals("INVALID_BINDING")||i.code().equals("CIRCULAR_DEPENDENCY")));}
    @Test void repositoryWritesAreScopedAndVersioned(){var w=workflow("w",List.of()); service.saveWorkflow(w,"t1"); org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.eq(CompositionRepository.Kind.WORKFLOW),org.mockito.ArgumentMatchers.eq("t1"),org.mockito.ArgumentMatchers.eq("w1"),org.mockito.ArgumentMatchers.eq("w"),org.mockito.ArgumentMatchers.eq("1.0"),org.mockito.ArgumentMatchers.eq(0L),org.mockito.ArgumentMatchers.any());}
    @Test void rejectsMissingAssetsAndEntitlements(){var w=new TemplateWorkflow("w","1.0","T",List.of(),List.of(),List.of(),Set.of(),Set.of(ExecutionMode.ASYNCHRONOUS),Set.of("video"),new CostEstimate(BigDecimal.ONE,"u",BigDecimal.ONE),new Reliability(true,true,1),Lifecycle.DRAFT,"t1","w1",0); assertTrue(service.validateWorkflow(w,"t1",Set.of()).issues().stream().anyMatch(i->i.code().equals("MISSING_ASSET"))); var a=new Application("a","1.0","A","",new ContractRef("x","1"),new ContractRef("y","1"),Set.of(),List.of(),Set.of(),Set.of("entitlement.compose"),Set.of(ExecutionMode.ASYNCHRONOUS),Lifecycle.DRAFT,"t1","w1",0); assertTrue(service.validateApplication(a,"t1",Set.of(),Set.of()).issues().stream().anyMatch(i->i.code().equals("MISSING_ENTITLEMENT")));}
}
