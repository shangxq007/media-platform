package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaDecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaDecisionServiceTest {

    @Test
    void decisionFacadeResolvesLimitAndDelegatesExplicitPrincipalAndPeriod() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-01T00:00:00Z");
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        PrincipalRef principal = PrincipalRef.tenantScoped(
                "tenant-1", PrincipalType.USER, "user-1");
        QuotaUsageAuthority authority = mock(QuotaUsageAuthority.class);
        QuotaDecisionService service = new QuotaDecisionService(new QuotaPolicyService(), authority);
        QuotaDecision expected = new QuotaDecision(
                principal, "render.job.create", 5, 10000, 10, true,
                CommercialDecisionReason.ALLOWED, List.of(), "quota-usage-v1", "trace-1", now);
        when(authority.decide(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        QuotaDecision actual = service.evaluate(
                principal, "render.job.create", start, end, 5, "trace-1", now);

        assertSame(expected, actual);
        ArgumentCaptor<QuotaUsageQuery> query = ArgumentCaptor.forClass(QuotaUsageQuery.class);
        verify(authority).decide(query.capture());
        assertEquals(principal, query.getValue().principal());
        assertEquals(start, query.getValue().periodStart());
        assertEquals(end, query.getValue().periodEnd());
        assertEquals(10000, query.getValue().limitUnits());
    }
}
