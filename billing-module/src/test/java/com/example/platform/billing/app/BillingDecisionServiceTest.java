package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingDecisionServiceTest {

    private BillingDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BillingDecisionService();
    }

    @Test
    void shouldApproveWithSufficientCredits() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.USAGE_BASED, 500, "USD", 1000L, true);
        BillingDecision decision = service.decideBilling("render", ctx);
        assertNotNull(decision);
        assertEquals(BillingDecision.STATUS_APPROVED, decision.status());
        assertTrue(decision.useCredits());
    }

    @Test
    void shouldApproveWithoutCredits() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.USAGE_BASED, 500, "USD", null, false);
        BillingDecision decision = service.decideBilling("render", ctx);
        assertNotNull(decision);
        assertEquals(BillingDecision.STATUS_APPROVED, decision.status());
        assertFalse(decision.useCredits());
    }

    @Test
    void shouldApproveZeroAmount() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.FREE_TRIAL, 0, "USD", 0L, true);
        BillingDecision decision = service.decideBilling("render", ctx);
        assertEquals(BillingDecision.STATUS_APPROVED, decision.status());
    }

    @Test
    void shouldDetectPartialCredit() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.USAGE_BASED, 1000, "USD", 500L, true);
        BillingDecision decision = service.decideBilling("render", ctx);
        assertNotNull(decision);
        assertEquals(BillingDecision.STATUS_APPROVED, decision.status());
        assertFalse(decision.useCredits());
    }

    @Test
    void shouldThrowOnNullAction() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.USAGE_BASED, 500, "USD", null, false);
        assertThrows(IllegalArgumentException.class, () -> service.decideBilling(null, ctx));
    }

    @Test
    void shouldThrowOnNullContext() {
        assertThrows(IllegalArgumentException.class, () -> service.decideBilling("render", null));
    }

    @Test
    void shouldGetDecision() {
        BillingDecisionService.BillingContext ctx = new BillingDecisionService.BillingContext(
                "t1", "u1", PricingModel.USAGE_BASED, 500, "USD", null, false);
        BillingDecision decision = service.decideBilling("render", ctx);
        BillingDecision found = service.getDecision(decision.decisionId());
        assertNotNull(found);
        assertEquals(decision.decisionId(), found.decisionId());
    }
}
