package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaPolicy;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaConsumptionRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuotaConsumptionApplicationServiceTest {
    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-1", PrincipalType.ORGANIZATION, "tenant-1");
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void delegatesStableIdempotentConsumptionToTheSoleQuotaAuthority() {
        QuotaPolicyService policies = mock(QuotaPolicyService.class);
        QuotaUsageAuthority authority = mock(QuotaUsageAuthority.class);
        when(policies.getQuotaPolicy("render.job.create")).thenReturn(
                new QuotaPolicy("qp-render", "default", "render.job.create", 100, "MONTHLY", 80));
        when(authority.execute(argThat(command ->
                command.idempotencyKey().equals("render-job:job-1:completion")
                        && command.limitValue() == 100
                        && command.operationKind() == QuotaOperationKind.CONSUMPTION)))
                .thenReturn(new QuotaUsageResult("op-1", PRINCIPAL, "render.job.create",
                        START, END, 1, 100, "render-job:job-1:completion",
                        QuotaOperationKind.CONSUMPTION, QuotaUsageOutcome.APPLIED,
                        0, 1, null, "trace-1", "render completion", START, START));

        var service = new QuotaConsumptionApplicationService(policies, authority);
        var decision = service.consume(new QuotaConsumptionRequest(
                PRINCIPAL, "render.job.create", 1, START, END,
                "render-job:job-1:completion", "trace-1", "render completion", START));

        assertTrue(decision.allowed());
        assertEquals(CommercialDecisionReason.ALLOWED, decision.reason());
        assertEquals(1, decision.usedUnits());
    }
}
