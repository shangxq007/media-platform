package com.example.platform.billing.infrastructure;

import com.example.platform.billing.app.BillingLedgerService;
import com.example.platform.billing.app.CreditWalletService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class BillingPersistenceBootstrap {

    private final CreditWalletJdbcRepository creditRepository;
    private final BillingLedgerJdbcRepository ledgerRepository;
    private final SubscriptionJdbcRepository subscriptionRepository;
    private final CreditWalletService creditWalletService;
    private final BillingLedgerService billingLedgerService;
    private final SubscriptionBillingService subscriptionBillingService;

    public BillingPersistenceBootstrap(CreditWalletJdbcRepository creditRepository,
                                       BillingLedgerJdbcRepository ledgerRepository,
                                       SubscriptionJdbcRepository subscriptionRepository,
                                       CreditWalletService creditWalletService,
                                       BillingLedgerService billingLedgerService,
                                       SubscriptionBillingService subscriptionBillingService) {
        this.creditRepository = creditRepository;
        this.ledgerRepository = ledgerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.creditWalletService = creditWalletService;
        this.billingLedgerService = billingLedgerService;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrate() {
        // CreditWalletService now uses JDBC as primary storage — no hydration needed
        for (BillingLedgerEntry entry : ledgerRepository.loadAll()) {
            billingLedgerService.hydrateEntry(entry);
        }
        for (SubscriptionPlan plan : subscriptionRepository.loadAllPlans()) {
            subscriptionBillingService.hydratePlan(plan);
        }
        for (SubscriptionContract contract : subscriptionRepository.loadAllContracts()) {
            subscriptionBillingService.hydrateContract(contract);
        }
    }
}
