package com.example.platform.commerce;

import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.shared.payment.PaymentSucceededPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentSucceededCheckoutHandler implements PaymentSucceededPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededCheckoutHandler.class);

    private final CheckoutOrchestrator checkoutOrchestrator;

    public PaymentSucceededCheckoutHandler(CheckoutOrchestrator checkoutOrchestrator) {
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @Override
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event.checkoutSessionId() == null || event.checkoutSessionId().isBlank()) {
            throw new IllegalArgumentException("checkoutSessionId required for payment fulfillment");
        }
        log.info("Payment succeeded for checkout session {} provider={}",
                event.checkoutSessionId(), event.providerCode());
        checkoutOrchestrator.confirmCheckout(event.checkoutSessionId(), event.userId());
    }
}
