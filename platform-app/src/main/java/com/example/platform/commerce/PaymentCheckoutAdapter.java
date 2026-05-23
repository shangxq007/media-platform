package com.example.platform.commerce;

import com.example.platform.payment.app.PaymentGatewayService;
import com.example.platform.payment.domain.CheckoutCommand;
import com.example.platform.payment.domain.CheckoutResult;
import com.example.platform.shared.commerce.CheckoutPaymentPort;
import org.springframework.stereotype.Service;

@Service
public class PaymentCheckoutAdapter implements CheckoutPaymentPort {

    private final PaymentGatewayService paymentGatewayService;

    public PaymentCheckoutAdapter(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @Override
    public CheckoutPaymentSession createPaymentForCheckout(CheckoutPaymentRequest request) {
        CheckoutResult result = paymentGatewayService.createCheckout(new CheckoutCommand(
                request.checkoutSessionId(),
                request.cartId() != null ? "cart:" + request.cartId() : request.productCode(),
                request.successUrl(),
                request.cancelUrl(),
                request.tenantId(),
                request.userId(),
                request.amountMinor(),
                request.currencyCode()));
        String providerCode = result.providerReference().startsWith("hs") ? "hyperswitch" : "stripe";
        return new CheckoutPaymentSession(providerCode, result.providerReference(), result.redirectUrl());
    }
}
