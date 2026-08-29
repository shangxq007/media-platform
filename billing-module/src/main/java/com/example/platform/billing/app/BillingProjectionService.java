package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingEvent;
import com.example.platform.billing.domain.BillingState;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, fail-closed read projection. It exposes no commercial mutation API. */
@Service
public class BillingProjectionService {

    private final SubscriptionJdbcRepository subscriptions;

    public BillingProjectionService(SubscriptionJdbcRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public BillingState currentState(PrincipalRef principal) {
        try {
            SubscriptionContract contract = subscriptions.findActive(principal, Instant.now()).stream()
                    .filter(value -> value.contractRole() == SubscriptionContractRole.BASE)
                    .findFirst().orElse(null);
            return contract == null ? null : new BillingState(contract.userId(),
                    contract.lifecycleState(), contract.periodEndAt(), contract.productCode());
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public SubscriptionContract getContract(PrincipalRef principal, String contractId) {
        try {
            return subscriptions.findByPrincipalAndId(principal, contractId).orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    public BillingEvent createBillingEvent(
            String eventType, String subjectId, String canonicalProductCode, String state) {
        return new BillingEvent(eventType, 1, subjectId, canonicalProductCode, state);
    }
}
