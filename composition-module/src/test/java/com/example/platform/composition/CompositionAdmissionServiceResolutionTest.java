package com.example.platform.composition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.composition.app.*;
import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.admission.ProviderBoundExecutionPlan;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompositionAdmissionServiceResolutionTest {
    @Test void productionEntryUsesAuthenticatedScopeAndResolverFacts() {
        var repo = mock(CompositionAdmissionRepository.class); var charge = mock(CompositionQuotaChargePort.class);
        var results = mock(CompositionResultRepository.class); var access = mock(CompositionAccess.class);
        when(access.resolve("workspace-selection")).thenReturn(new CompositionAccess.Scope("tenant", "workspace", "actor"));
        var workflow = workflow();
        var revisions = (CompositionPublishedRevisionAuthority) (t,w,id,v) -> Optional.of(new CompositionPublishedRevisionAuthority.PublishedRevision(workflow, "server-fingerprint"));
        var resources = (CompositionResourceResolver) (wf,t,w) -> new CompositionResourceResolver.ResourceResolution(Set.of());
        var entitlements = (CompositionEntitlementQuotaResolver) (wf,t,w,a) ->
                new ProviderBoundExecutionPlan.EntitlementQuotaSnapshot("server-quota", Map.of("composition", "granted"), new BigDecimal("0.10"));
        var accepted = new java.util.concurrent.atomic.AtomicReference<CompositionAdmissionRepository.AdmissionRecord>();
        when(repo.admit(any())).thenAnswer(inv -> {
            var p = inv.getArgument(0, ProviderBoundExecutionPlan.class);
            var record = new CompositionAdmissionRepository.AdmissionRecord("execution", "tenant", "workspace", "actor", "composition", 1, "server-fingerprint", "key", "hash", 0, "ADMITTED", true, true, p);
            accepted.set(record); return record;
        });
        when(repo.claimQuotaCharge(any())).thenReturn(true);
        when(repo.findByExecutionId(any())).thenAnswer(inv -> Optional.of(accepted.get()));
        var service = new CompositionAdmissionService(repo, charge, results, access, revisions, authority(), resources, entitlements);
        var plan = service.admit(new CompositionAdmissionRequest("workspace-selection", "composition", "1.0", "key", "hash", "cancel", "retry", Map.of()));
        assertEquals("server-fingerprint", plan.plan().planFingerprint().value());
        verify(access).resolve("workspace-selection"); verify(repo).admit(plan.plan()); verify(charge).charge(plan.plan(), "execution");
        assertThrows(IllegalArgumentException.class, () -> service.admit(mock(com.example.platform.execution.planning.PlatformExecutionPlan.class)));
    }

    private static TemplateWorkflow workflow() {
        return new TemplateWorkflow("composition", "1.0", "Composition",
                List.of(new WorkflowStep("source", "media.thumbnail", "1.0", Map.of(), Set.of(), Set.of())), List.of(), List.of(),
                Set.of("media.thumbnail"), Set.of(ExecutionMode.ASYNCHRONOUS), Set.of(),
                List.of(new WorkflowOutput("result", "ImageAsset", "source", "output")),
                new WorkflowEntry("input", new ContractRef("MediaAsset", "1"), "source", "input"),
                new CostEstimate(BigDecimal.ONE, "quota", new BigDecimal("0.10")), new Reliability(true, true, 1), Lifecycle.PUBLISHED, "tenant", "workspace", 1);
    }
    private static CompositionProviderBoundCapabilityAuthority authority() {
        return new CompositionProviderBoundCapabilityAuthority() {
            public List<CapabilityAvailability> publicAvailability() { return List.of(new CapabilityAvailability("media.thumbnail", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("ImageAsset", "1"), Set.of(), Set.of(), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "ok", new CostEstimate(BigDecimal.ONE, "quota", new BigDecimal("0.10")), new Reliability(true, true, 1), Set.of(), Set.of())); }
            public Optional<CapabilityAvailability> resolve(String id, String version) { return publicAvailability().stream().filter(c -> c.capabilityId().equals(id) && c.version().equals(version)).findFirst(); }
            public Optional<ProviderBoundCapability> resolveProviderBound(String id, String version) { return Optional.of(new ProviderBoundCapability(id, version, "registry:provider", "1.0", "MediaAsset", "1", "ImageAsset", "1")); }
        };
    }
}
