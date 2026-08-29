package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotaUsageServiceTest {

    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");

    @Test
    void compatibilityFacadeDelegatesCanonicalCommandWithoutLocalState() {
        QuotaUsageAuthority authority = mock(QuotaUsageAuthority.class);
        QuotaUsageService service = new QuotaUsageService(authority);
        PrincipalRef principal = PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1");
        QuotaUsageCommand command = new QuotaUsageCommand(
                principal, "render", START, END, 5, 100, "idem-1",
                QuotaOperationKind.CONSUMPTION, "trace-1", "usage", NOW);
        QuotaUsageResult expected = new QuotaUsageResult(
                "op-1", principal, "render", START, END, 5, 100, "idem-1",
                QuotaOperationKind.CONSUMPTION, QuotaUsageOutcome.APPLIED,
                0, 5, null, "trace-1", "usage", NOW, NOW);
        when(authority.execute(command)).thenReturn(expected);

        assertSame(expected, service.execute(command));
        verify(authority).execute(command);
    }

    @Test
    void compatibilityFacadeDelegatesExplicitScopedRead() {
        QuotaUsageAuthority authority = mock(QuotaUsageAuthority.class);
        QuotaUsageService service = new QuotaUsageService(authority);
        QuotaUsageQuery query = new QuotaUsageQuery(
                PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1"),
                "render", START, END, 0, 100, "trace-read", NOW);
        when(authority.currentUsage(query)).thenReturn(17L);

        org.junit.jupiter.api.Assertions.assertEquals(17L, service.currentUsage(query));
        verify(authority).currentUsage(query);
    }
}
