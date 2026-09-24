package com.example.platform.composition;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.composition.app.*;
import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompositionProviderBoundExecutionPlanAdapterTest {
    private static final CompositionProviderBoundCapabilityAuthority AUTHORITY = new Authority();
    private static final CompositionPublishedRevisionAuthority REVISIONS = (tenant, workspace, id, version) -> Optional.of(published(workflow(Lifecycle.PUBLISHED)));
    private static final CompositionResourceResolver RESOURCES = (workflow, tenant, workspace) -> new CompositionResourceResolver.ResourceResolution(Set.of());
    private static final CompositionEntitlementQuotaResolver QUOTA = (workflow, tenant, workspace, actor) -> new ProviderBoundExecutionPlan.EntitlementQuotaSnapshot("ent-1", Map.of("compose", "granted"), BigDecimal.valueOf(3));

    @Test
    void lowersPublishedWorkflowWithTypedProviderBindingAndIo() {
        var plan = lower(workflow(Lifecycle.PUBLISHED), AUTHORITY);

        assertEquals("1.0", plan.publishedRevision().version());
        assertEquals(1, plan.publishedRevision().revision());
        assertEquals("provider.test", plan.capabilityBindings().getFirst().providerIdentity().registryReference());
        assertEquals("ImageAsset", plan.outputs().getFirst().contract());
        assertEquals(ProviderBoundExecutionPlan.ExecutionMode.ASYNCHRONOUS, plan.executionMode());
    }

    @Test
    void rejectsUnpublishedRevision() {
        assertThrows(IllegalArgumentException.class, () -> lower(workflow(Lifecycle.DRAFT)));
    }

    @Test
    void rejectsScopeMismatchAndMissingProviderBinding() {
        assertThrows(IllegalArgumentException.class, () ->
                CompositionProviderBoundExecutionPlanAdapter.lower(REVISIONS, AUTHORITY, RESOURCES, QUOTA,
                        new ProviderBoundExecutionPlan.Scope("other", "workspace", "actor"), "composition", "1.0",
                        "key", "hash", "cancel", "retry"));
        var missing = new Authority(true);
        assertThrows(IllegalArgumentException.class, () -> lower(workflow(Lifecycle.PUBLISHED), missing));
    }

    private static ProviderBoundExecutionPlan lower(TemplateWorkflow workflow) {
        return lower(workflow, AUTHORITY);
    }
    private static ProviderBoundExecutionPlan lower(TemplateWorkflow workflow,
            CompositionProviderBoundCapabilityAuthority authority) {
        CompositionPublishedRevisionAuthority revisions = (tenant, workspace, id, version) -> Optional.of(published(workflow));
        return CompositionProviderBoundExecutionPlanAdapter.lower(revisions, authority, RESOURCES, QUOTA,
                new ProviderBoundExecutionPlan.Scope("tenant", "workspace", "actor"), "composition", "1.0",
                "key", "hash", "cancel", "retry");
    }
    private static CompositionPublishedRevisionAuthority.PublishedRevision published(TemplateWorkflow workflow) {
        return new CompositionPublishedRevisionAuthority.PublishedRevision(workflow, "fp-1");
    }
    private static TemplateWorkflow workflow(Lifecycle lifecycle) {
        return new TemplateWorkflow("composition", "1.0", "Composition",
                List.of(new WorkflowStep("source", "media.thumbnail", "1.0", Map.of(), Set.of(), Set.of())),
                List.of(), List.of(), Set.of("media.thumbnail"), Set.of(ExecutionMode.ASYNCHRONOUS), Set.of(),
                List.of(new WorkflowOutput("result", "ImageAsset", "source", "output")),
                new WorkflowEntry("input", new ContractRef("MediaAsset", "1"), "source", "input"),
                new CostEstimate(BigDecimal.ONE, "quota", BigDecimal.ONE), new Reliability(true, true, 1),
                lifecycle, "tenant", "workspace", 1);
    }

    private static final class Authority implements CompositionProviderBoundCapabilityAuthority {
        private final boolean missing;
        private Authority() { this(false); }
        private Authority(boolean missing) { this.missing = missing; }
        @Override public List<CapabilityAvailability> publicAvailability() {
            return List.of(new CapabilityAvailability("media.thumbnail", "1.0",
                    new ContractRef("MediaAsset", "1"), new ContractRef("ImageAsset", "1"), Set.of(), Set.of(),
                    Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "test",
                    new CostEstimate(BigDecimal.ONE, "quota", BigDecimal.ONE), new Reliability(true, true, 1), Set.of(), Set.of()));
        }
        @Override public Optional<CapabilityAvailability> resolve(String id, String version) {
            return publicAvailability().stream().filter(x -> x.capabilityId().equals(id) && x.version().equals(version)).findFirst();
        }
        @Override public Optional<ProviderBoundCapability> resolveProviderBound(String id, String version) {
            if (missing) return Optional.empty();
            return Optional.of(new ProviderBoundCapability(id, version, "provider.test", "contract-1",
                    "MediaAsset", "1", "ImageAsset", "1"));
        }
    }
}
