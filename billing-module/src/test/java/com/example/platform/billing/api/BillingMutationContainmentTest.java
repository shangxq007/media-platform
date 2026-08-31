package com.example.platform.billing.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.billing.api.dto.CancelSubscriptionRequest;
import com.example.platform.billing.api.dto.ChangePlanRequest;
import com.example.platform.billing.api.dto.CreateSubscriptionRequest;
import com.example.platform.billing.app.BillingCycleService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingMutationContainmentTest {

    @Test
    void requestTenantUserAndActorCannotAuthorizeSubscriptionOrCycleMutations() {
        SubscriptionBillingService subscriptions = mock(SubscriptionBillingService.class);
        BillingCycleService cycles = mock(BillingCycleService.class);
        AdminAuditPublisher audit = mock(AdminAuditPublisher.class);
        SubscriptionBillingController controller = new SubscriptionBillingController(subscriptions, audit);
        BillingCycleController cycleController = new BillingCycleController(cycles, subscriptions);
        Instant now = Instant.parse("2026-08-31T00:00:00Z");

        assertUnavailable(() -> controller.createSubscription(new CreateSubscriptionRequest(
                "request-tenant", "request-user", "contract", "plan", 30,
                "idempotency", "request-actor", "reason", "trace", now)));
        assertUnavailable(() -> controller.changePlan(new ChangePlanRequest(
                "request-tenant", "request-user", "contract", "new-plan", 30, 1,
                "idempotency", "request-actor", "reason", "trace", now)));
        assertUnavailable(() -> controller.cancel(new CancelSubscriptionRequest(
                "request-tenant", "request-user", "contract", 1,
                "idempotency", "request-actor", "reason", "trace", now)));
        assertUnavailable(() -> cycleController.runCycle("request-tenant", "request-user"));
        assertUnavailable(cycleController::processDueSubscriptions);

        verifyNoInteractions(subscriptions, cycles, audit);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
