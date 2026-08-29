package com.example.platform.payment;

import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.payment.app.PaymentSettlementProjectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Consumes only the durable Payment settlement outbox projection. */
@Service
public class PaymentSettledCheckoutProjectionHandler implements PaymentSettlementProjectionPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettledCheckoutProjectionHandler.class);

    private final CheckoutOrchestrator checkoutOrchestrator;

    public PaymentSettledCheckoutProjectionHandler(CheckoutOrchestrator checkoutOrchestrator) {
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @Override
    public void onPaymentSettled(PaymentSettledEvent event) {
        if (event.checkoutSessionId() == null || event.checkoutSessionId().isBlank()) {
            throw new IllegalArgumentException("checkoutSessionId required for payment fulfillment");
        }
        log.info("Payment settled for checkout session {} provider={}",
                event.checkoutSessionId(), event.providerCode());
        checkoutOrchestrator.confirmCheckout(event.checkoutSessionId(), null);
    }
}
