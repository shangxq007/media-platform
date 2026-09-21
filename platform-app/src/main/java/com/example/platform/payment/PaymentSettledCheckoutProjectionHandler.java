package com.example.platform.payment;

import com.example.platform.commerce.api.checkout.CheckoutOrderPort;
import com.example.platform.payment.app.PaymentSettlementProjectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.TenantGuard;

/** Consumes only the durable Payment settlement outbox projection. */
@Service
public class PaymentSettledCheckoutProjectionHandler implements PaymentSettlementProjectionPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettledCheckoutProjectionHandler.class);

    private final CheckoutOrderPort checkoutOrchestrator;

    public PaymentSettledCheckoutProjectionHandler(CheckoutOrderPort checkoutOrchestrator) {
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @Override
    public void onPaymentSettled(PaymentSettledEvent event) {
        if (event.checkoutSessionId() == null || event.checkoutSessionId().isBlank()) {
            throw new IllegalArgumentException("checkoutSessionId required for payment fulfillment");
        }
        log.info("Payment settled for checkout session {} provider={}",
                event.checkoutSessionId(), event.providerCode());
        if (event.tenantId() == null || event.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId required for payment fulfillment");
        }
        TenantGuard.assertSameTenantIfContextPresent(event.tenantId());
        String previousTenant = TenantContext.get();
        try {
            // This scope comes from the claimed Payment outbox row, not HTTP actor/scope hints.
            // Commerce resolves the principal from its persisted checkout, never from the event.
            TenantContext.set(event.tenantId());
            checkoutOrchestrator.confirmCheckout(event.checkoutSessionId());
        } finally {
            if (previousTenant == null) TenantContext.clear();
            else TenantContext.set(previousTenant);
        }
    }
}
