package com.example.platform.shared.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CommercialAuthorityPortContractTest {

    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-1", PrincipalType.ORGANIZATION, "tenant-1");
    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void admissionRequestCarriesNeutralPrincipalAndSeparateEntitlementAndQuotaKeys() {
        CommercialAdmissionRequest request = new CommercialAdmissionRequest(
                PRINCIPAL, "render.submit", "render.job.create", "render.job.create",
                1, START, END, "trace-1", START);

        assertEquals("render.job.create", request.entitlementKey());
        assertEquals("render.job.create", request.quotaKey());
        assertEquals(PRINCIPAL, request.principal());
    }

    @Test
    void consumptionRequestRequiresStableIdempotencyAndExplicitPeriod() {
        QuotaConsumptionRequest request = new QuotaConsumptionRequest(
                PRINCIPAL, "render.job.create", 1, START, END,
                "render-job:job-1:completion", "trace-1", "render completion", START);

        assertEquals("render-job:job-1:completion", request.idempotencyKey());
        assertThrows(IllegalArgumentException.class, () -> new QuotaConsumptionRequest(
                PRINCIPAL, "render.job.create", 1, START, END,
                " ", "trace-1", "render completion", START));
    }
}
