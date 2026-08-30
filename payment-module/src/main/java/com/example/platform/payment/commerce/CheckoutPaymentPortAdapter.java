package com.example.platform.payment.commerce;

import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.infrastructure.PaymentRoutingProperties;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.commerce.app.CheckoutPaymentPort;
import org.springframework.stereotype.Service;

@Service
public class CheckoutPaymentPortAdapter implements CheckoutPaymentPort {

    private final PaymentTransactionAuthority authority;
    private final PaymentRoutingProperties routing;

    public CheckoutPaymentPortAdapter(PaymentTransactionAuthority authority,
                                      PaymentRoutingProperties routing) {
        this.authority = authority;
        this.routing = routing;
    }

    @Override
    public CheckoutPaymentSession createPaymentForCheckout(CheckoutPaymentRequest request) {
        String transactionId = "ptx_" + request.checkoutSessionId();
        PrincipalRef principal = PrincipalRef.tenantScoped(
                request.tenantId(), PrincipalType.USER, request.userId());
        PaymentTransaction result = authority.initiateCheckout(new InitiateCheckoutCommand(
                transactionId, principal, request.cartId(), request.checkoutSessionId(),
                routing.getDefaultProviderCode(), new Money(request.amountMinor(), request.currencyCode()),
                request.productCode(), request.successUrl(), request.cancelUrl(),
                request.idempotencyKey(), "commerce", "initiate checkout",
                request.traceId(), request.occurredAt()));
        return new CheckoutPaymentSession(result.providerCode(), result.providerReference(), result.redirectUrl());
    }
}
