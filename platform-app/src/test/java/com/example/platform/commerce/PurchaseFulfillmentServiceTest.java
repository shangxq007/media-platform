package com.example.platform.commerce;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.billing.app.BillingLedgerService;
import com.example.platform.billing.app.CreditWalletService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.commerce.app.PurchaseFulfillmentCommand;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PurchaseFulfillmentServiceTest {

    @Test
    void baseSubscriptionUsesCanonicalOrganizationGrantInsteadOfTierCapabilityMutation() {
        SubscriptionBillingService subscriptions = mock(SubscriptionBillingService.class);
        when(subscriptions.getPlan("pro_monthly")).thenReturn(mock(SubscriptionPlan.class));
        EntitlementService entitlements = mock(EntitlementService.class);
        PurchaseFulfillmentService service = new PurchaseFulfillmentService(
                subscriptions, mock(CreditWalletService.class), mock(BillingLedgerService.class),
                entitlements, Optional.empty());

        service.fulfill(new PurchaseFulfillmentCommand(
                "order-1", "tenant-1", "user-1", "pro_monthly", "SUBSCRIPTION",
                "BASE_SUBSCRIPTION", "pro_monthly", "PRO", "default_features",
                "pro_quota", null, null, null, 30, Instant.parse("2026-08-30T00:00:00Z")));

        ArgumentCaptor<EntitlementGrantCommand> grant =
                ArgumentCaptor.forClass(EntitlementGrantCommand.class);
        verify(entitlements).execute(grant.capture());
        var subscription=ArgumentCaptor.forClass(com.example.platform.billing.domain.SubscriptionCommand.class);
        verify(subscriptions).execute(subscription.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ORGANIZATION",subscription.getValue().principal().principalType().name());
        org.junit.jupiter.api.Assertions.assertEquals("tenant-1",subscription.getValue().principal().principalId());
        org.junit.jupiter.api.Assertions.assertNotEquals("user-1",subscription.getValue().principal().principalId());
        org.junit.jupiter.api.Assertions.assertEquals("default_features", grant.getValue().bundleCode());
        org.junit.jupiter.api.Assertions.assertEquals("ORGANIZATION",
                grant.getValue().principal().principalType().name());
    }

    @Test void unsupportedSeatAllocationCannotWriteATenantAsWorkspaceOrClaimSuccess() {
        var subscriptions=mock(SubscriptionBillingService.class);var wallets=mock(CreditWalletService.class);var ledger=mock(BillingLedgerService.class);
        var entitlements=mock(EntitlementService.class);var pools=mock(com.example.platform.entitlement.app.WorkspaceEntitlementPoolService.class);
        var service=new PurchaseFulfillmentService(subscriptions,wallets,ledger,entitlements,Optional.of(pools));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->service.fulfill(new PurchaseFulfillmentCommand(
                "seat-order","tenant","purchaser","seats","SEAT_PACK","SEAT_PACK",null,null,null,null,null,1,"render.minutes",30,Instant.now())));
        org.mockito.Mockito.verifyNoInteractions(subscriptions,wallets,ledger,entitlements,pools);
    }
}
