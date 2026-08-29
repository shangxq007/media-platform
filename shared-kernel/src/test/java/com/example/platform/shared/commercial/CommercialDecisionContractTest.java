package com.example.platform.shared.commercial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class CommercialDecisionContractTest {

    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-1", PrincipalType.USER, "user-1");
    private static final Instant DECIDED_AT = Instant.parse("2026-08-29T00:00:00Z");
    private static final CommercialEvidenceRef EVIDENCE = new CommercialEvidenceRef(
            "entitlement", "grant", "grant-1");

    @Test
    void reasonSetIsExactlyAcceptedGenericReasonsPlusAllowed() {
        assertEquals(Set.of(
                        "ALLOWED",
                        "NOT_ENTITLED",
                        "POLICY_DENIED",
                        "QUOTA_EXCEEDED",
                        "SUBSCRIPTION_INACTIVE",
                        "COMMERCIAL_ACCOUNT_SUSPENDED",
                        "BILLING_ACTION_REQUIRED",
                        "PAYMENT_FAILED",
                        "TRIAL_EXPIRED"),
                Arrays.stream(CommercialDecisionReason.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }

    @Test
    void carriesStructuredEvidenceAuthorityVersionTraceAndTime() {
        CommercialDecision decision = new CommercialDecision(
                PRINCIPAL,
                "render.job.create",
                true,
                CommercialDecisionReason.ALLOWED,
                List.of(EVIDENCE),
                "entitlement-v7",
                "trace-1",
                DECIDED_AT);

        assertEquals(PRINCIPAL, decision.principal());
        assertEquals("render.job.create", decision.action());
        assertEquals(List.of(EVIDENCE), decision.evidence());
        assertEquals("entitlement-v7", decision.authorityVersion());
        assertEquals("trace-1", decision.traceId());
        assertEquals(DECIDED_AT, decision.decidedAt());
        assertThrows(UnsupportedOperationException.class,
                () -> decision.evidence().add(EVIDENCE));
    }

    @Test
    void enforcesAllowedReasonConsistency() {
        assertThrows(IllegalArgumentException.class, () -> new CommercialDecision(
                PRINCIPAL, "render.job.create", true,
                CommercialDecisionReason.QUOTA_EXCEEDED, List.of(),
                "quota-v1", "trace-1", DECIDED_AT));
        assertThrows(IllegalArgumentException.class, () -> new CommercialDecision(
                PRINCIPAL, "render.job.create", false,
                CommercialDecisionReason.ALLOWED, List.of(),
                "quota-v1", "trace-1", DECIDED_AT));
    }

    @Test
    void genericDecisionExposesNoProviderNativeStatusSurface() {
        Set<String> componentNames = Arrays.stream(CommercialDecision.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        assertFalse(componentNames.stream().anyMatch(name -> name.contains("provider")));
        assertFalse(componentNames.stream().anyMatch(name -> name.contains("status")));
    }
}
