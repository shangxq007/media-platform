package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaConsumptionBoundaryTest {

    @Test
    void compatibilityBoundaryDelegatesCompleteCanonicalCommand() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-01T00:00:00Z");
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        PrincipalRef principal = PrincipalRef.tenantScoped(
                "tenant-1", PrincipalType.USER, "user-1");
        QuotaUsageCommand command = new QuotaUsageCommand(
                principal, "render", start, end, 5, 100, "idem-1",
                QuotaOperationKind.CONSUMPTION, "trace-1", "post execution", now);
        QuotaUsageResult expected = new QuotaUsageResult(
                "op-1", principal, "render", start, end, 5, 100, "idem-1",
                QuotaOperationKind.CONSUMPTION, QuotaUsageOutcome.APPLIED,
                0, 5, null, "trace-1", "post execution", now, now);
        QuotaUsageAuthority authority = mock(QuotaUsageAuthority.class);
        when(authority.execute(command)).thenReturn(expected);
        QuotaConsumptionBoundary boundary = new QuotaConsumptionBoundaryImpl(authority);

        assertSame(expected, boundary.recordPostExecutionUsage(command));
        verify(authority).execute(command);
    }
}
