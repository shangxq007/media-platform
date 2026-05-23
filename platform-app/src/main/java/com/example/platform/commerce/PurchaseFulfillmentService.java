package com.example.platform.commerce;

import com.example.platform.billing.app.BillingLedgerService;
import com.example.platform.billing.app.CreditWalletService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.commerce.domain.ProductLineType;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.entitlement.domain.EntitlementGrant;
import com.example.platform.entitlement.domain.EntitlementGrantStatus;
import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commerce.PurchaseFulfillmentCommand;
import com.example.platform.shared.commerce.PurchaseFulfillmentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies confirmed purchases to billing contracts and entitlement state (P0/P1 catalog).
 */
@Service
public class PurchaseFulfillmentService implements PurchaseFulfillmentPort {

    private static final Logger log = LoggerFactory.getLogger(PurchaseFulfillmentService.class);

    private final SubscriptionBillingService subscriptionBillingService;
    private final CreditWalletService creditWalletService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final EntitlementService entitlementService;
    private final Optional<WorkspaceEntitlementPoolService> workspaceEntitlementPoolService;
    private final BillingLedgerService billingLedgerService;

    public PurchaseFulfillmentService(
            SubscriptionBillingService subscriptionBillingService,
            CreditWalletService creditWalletService,
            BillingLedgerService billingLedgerService,
            EntitlementPolicyService entitlementPolicyService,
            EntitlementService entitlementService,
            Optional<WorkspaceEntitlementPoolService> workspaceEntitlementPoolService) {
        this.subscriptionBillingService = subscriptionBillingService;
        this.creditWalletService = creditWalletService;
        this.billingLedgerService = billingLedgerService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.entitlementService = entitlementService;
        this.workspaceEntitlementPoolService = workspaceEntitlementPoolService;
    }

    @Override
    public void fulfill(PurchaseFulfillmentCommand command) {
        ProductLineType lineType = ProductLineType.valueOf(command.lineType());
        switch (lineType) {
            case BASE_SUBSCRIPTION -> fulfillBaseSubscription(command);
            case ADD_ON_SUBSCRIPTION -> fulfillAddonSubscription(command);
            case CREDIT_PACK -> fulfillCreditPack(command);
            case SEAT_PACK -> fulfillSeatPack(command);
            default -> throw new IllegalArgumentException("Unsupported line type: " + command.lineType());
        }
        billingLedgerService.writeEntry(
                command.tenantId(),
                null,
                command.userId(),
                BillingLedgerEntry.TYPE_CHARGE,
                0L,
                "USD",
                "PURCHASE",
                command.orderId(),
                "Purchase " + command.productCode() + " (" + command.lineType() + ")");
        log.info("Fulfilled order {} product {} lineType {} tenant={}",
                command.orderId(), command.productCode(), command.lineType(), command.tenantId());
    }

    private void fulfillBaseSubscription(PurchaseFulfillmentCommand command) {
        if (command.tierKey() != null && !command.tierKey().isBlank()) {
            entitlementPolicyService.setTier(command.tenantId(), command.tierKey());
        }
        ensurePlan(command.planKey(), command.productCode(), Map.of());
        subscriptionBillingService.createSubscription(
                command.tenantId(),
                command.userId(),
                command.planKey(),
                command.productCode(),
                command.periodDays(),
                SubscriptionContractRole.BASE);
    }

    private void fulfillAddonSubscription(PurchaseFulfillmentCommand command) {
        ensurePlan(command.planKey(), command.productCode(), Map.of("addon", 1L));
        subscriptionBillingService.createAddonSubscription(
                command.tenantId(),
                command.userId(),
                command.planKey(),
                command.productCode(),
                command.periodDays());

        if (command.bundleKey() != null && !command.bundleKey().isBlank()) {
            Instant now = Instant.now();
            EntitlementGrant grant = new EntitlementGrant(
                    Ids.newId("ent_grant"),
                    command.tenantId(),
                    null,
                    "TENANT",
                    command.tenantId(),
                    command.bundleKey(),
                    command.bundleKey(),
                    command.quotaProfileCode(),
                    "commerce",
                    "purchase:" + command.orderId(),
                    "system",
                    now,
                    now.plus(command.periodDays(), ChronoUnit.DAYS),
                    null,
                    null,
                    null,
                    EntitlementGrantStatus.ACTIVE,
                    now,
                    now);
            entitlementService.grantEntitlement(grant);
        }
    }

    private void fulfillCreditPack(PurchaseFulfillmentCommand command) {
        long amount = command.creditAmountMinor() != null ? command.creditAmountMinor() : 0L;
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit pack requires creditAmountMinor");
        }
        CreditWallet wallet = creditWalletService.getWalletByTenant(command.tenantId(), command.userId());
        if (wallet == null) {
            wallet = creditWalletService.createWallet(
                    command.tenantId(), null, command.userId(), "USD");
        }
        creditWalletService.credit(
                wallet.walletId(),
                amount,
                "PURCHASE",
                command.orderId(),
                "Credit pack " + command.productCode());
    }

    private void fulfillSeatPack(PurchaseFulfillmentCommand command) {
        int seats = command.includedSeats() != null ? command.includedSeats() : 0;
        if (seats <= 0) {
            throw new IllegalArgumentException("Seat pack requires includedSeats");
        }
        String featureKey = command.seatFeatureKey() != null && !command.seatFeatureKey().isBlank()
                ? command.seatFeatureKey()
                : "render.minutes";
        long additionalMinutes = seats * 60L;
        workspaceEntitlementPoolService.ifPresentOrElse(poolService -> {
            List<WorkspaceEntitlementPool> pools = poolService.getPool(command.tenantId());
            boolean exists = pools.stream().anyMatch(p -> featureKey.equals(p.featureKey()));
            if (!exists) {
                poolService.createPool(command.tenantId(), featureKey, additionalMinutes, "MONTHLY", "commerce");
            } else {
                poolService.extendPoolQuota(command.tenantId(), featureKey, additionalMinutes, "commerce");
            }
        }, () -> log.warn("WorkspaceEntitlementPoolService unavailable; seat pack not applied for order {}",
                command.orderId()));
    }

    private void ensurePlan(String planKey, String productCode, Map<String, Long> defaultQuota) {
        if (planKey == null || planKey.isBlank()) {
            throw new IllegalArgumentException("Plan key required for subscription fulfillment");
        }
        if (subscriptionBillingService.getPlan(planKey) == null) {
            subscriptionBillingService.createPlan(
                    planKey,
                    productCode,
                    "Catalog plan " + productCode,
                    "MONTHLY",
                    0L,
                    "USD",
                    defaultQuota);
        }
    }
}
