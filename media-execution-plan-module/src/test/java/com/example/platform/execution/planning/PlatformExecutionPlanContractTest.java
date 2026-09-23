package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlatformExecutionPlanContractTest {
    private PlatformExecutionPlan plan() {
        return new PlatformExecutionPlan(
                new PlatformExecutionPlan.Scope("tenant", "workspace", "actor"),
                new PlatformExecutionPlan.SourceRevision("composition", "workflow", "7"),
                new ExecutionPlanId("ep-1"),
                new PlatformExecutionPlan.OperationIdentity("capability", "operation"),
                List.of(new PlatformExecutionPlan.TypedInputReference("in", "MediaAsset", "1", "asset:1")),
                List.of(new PlatformExecutionPlan.TypedOutputExpectation("out", "MediaAsset", "1", "artifact")),
                PlatformExecutionPlan.ExecutionMode.ASYNCHRONOUS,
                new PlatformExecutionPlan.IdempotencyIdentity("idem-1", "hash-1"),
                new PlatformExecutionPlan.EntitlementQuotaSnapshot("q-1", Map.of("compose", "granted"), 1),
                new PlatformExecutionPlan.CorrelationAuditIdentity("corr-1", "audit-1"));
    }
    @Test void carriesAllPlatformOwnedAdmissionDimensions() {
        var p = plan();
        assertEquals("tenant", p.scope().tenantId());
        assertEquals("composition", p.source().domain());
        assertEquals("ep-1", p.planId().value());
        assertEquals("capability", p.operation().capability());
        assertEquals("MediaAsset", p.inputs().getFirst().contract());
        assertEquals("MediaAsset", p.outputs().getFirst().contract());
        assertEquals("idem-1", p.idempotency().key());
        assertEquals(1, p.quota().quotaUnits());
    }
    @Test void rejectsMissingTypedIoAndNegativeQuota() {
        assertThrows(IllegalArgumentException.class, () -> new PlatformExecutionPlan(
                plan().scope(), plan().source(), plan().planId(), plan().operation(), List.of(), plan().outputs(),
                plan().executionMode(), plan().idempotency(), plan().quota(), plan().audit()));
        assertThrows(IllegalArgumentException.class, () -> new PlatformExecutionPlan.EntitlementQuotaSnapshot("q", Map.of(), -1));
    }
}
