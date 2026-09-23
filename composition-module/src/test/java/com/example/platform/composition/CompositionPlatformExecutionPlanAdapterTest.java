package com.example.platform.composition;

import com.example.platform.composition.app.*;
import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.PlatformExecutionPlan;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositionPlatformExecutionPlanAdapterTest {
    private TemplateWorkflow valid(String tenant, String workspace) {
        var step = new WorkflowStep("source", "media.thumbnail", "1.0", Map.of(), Set.of(), Set.of());
        return new TemplateWorkflow("workflow", "1.0", "Workflow", List.of(step), List.of(), List.of(),
                Set.of("media.thumbnail"), Set.of(ExecutionMode.ASYNCHRONOUS), Set.of(),
                List.of(new WorkflowOutput("result", "ImageAsset", "source", "output")),
                new WorkflowEntry("input", new ContractRef("MediaAsset", "1"), "source", "input"),
                new CostEstimate(BigDecimal.ONE, "unit", BigDecimal.ONE), new Reliability(true, true, 1),
                Lifecycle.DRAFT, tenant, workspace, 3);
    }
    private PlatformExecutionPlan.Scope scope(String t, String w) { return new PlatformExecutionPlan.Scope(t, w, "actor"); }
    private PlatformExecutionPlan adapt(TemplateWorkflow workflow, PlatformExecutionPlan.Scope scope) {
        return CompositionPlatformExecutionPlanAdapter.adapt(workflow, new TestProviderRegistry(), Set.of(), Set.of(),
                scope, new ExecutionPlanId("ep-1"), "idem", "hash", "quota", 1,
                new PlatformExecutionPlan.CorrelationAuditIdentity("corr", "audit"));
    }
    @Test void validCompositionProducesGenericPlan() {
        var p = adapt(valid("t1", "w1"), scope("t1", "w1"));
        assertEquals("composition", p.source().domain());
        assertEquals("media.thumbnail", p.operation().capability());
        assertEquals(PlatformExecutionPlan.ExecutionMode.ASYNCHRONOUS, p.executionMode());
    }
    @Test void tenantWorkspaceMismatchFailsBeforeAdmission() {
        assertThrows(IllegalArgumentException.class, () -> adapt(valid("t1", "w1"), scope("other", "w1")));
    }
    @Test void invalidEntryAndOutputFailBeforeAdmission() {
        var w = valid("t1", "w1");
        var invalid = new TemplateWorkflow(w.id(), w.version(), w.name(), w.steps(), w.bindings(), w.parameters(),
                w.requiredCapabilities(), Set.of(), w.requiredAssets(), w.outputs(), w.entry(), w.estimate(),
                w.reliability(), w.lifecycle(), w.tenantId(), w.workspaceId(), w.revision());
        assertThrows(IllegalArgumentException.class, () -> adapt(invalid, scope("t1", "w1")));
    }
}
