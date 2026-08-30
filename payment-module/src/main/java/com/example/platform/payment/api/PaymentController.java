package com.example.platform.payment.api;

import com.example.platform.payment.api.dto.ConfirmPaymentRequest;
import com.example.platform.payment.api.dto.RefundPaymentRequest;
import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.RefundPaymentCommand;
import com.example.platform.payment.domain.RefundResult;
import com.example.platform.payment.domain.VerifyPaymentCommand;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentTransactionAuthority authority;

    public PaymentController(PaymentTransactionAuthority authority) {
        this.authority = authority;
    }

    @PostMapping("/confirm")
    public PaymentTransaction confirm(@RequestBody ConfirmPaymentRequest request) {
        return authority.verifyPayment(new VerifyPaymentCommand(principal(request),
                request.transactionId(), request.providerCode(), request.providerReference(),
                request.expectedVersion(), request.idempotencyKey(), "payment-api",
                request.reason(), request.traceId(), request.occurredAt()));
    }

    @PostMapping("/refunds")
    public RefundResult refund(@RequestBody RefundPaymentRequest request) {
        PrincipalRef principal = new PrincipalRef(request.tenantId(),
                PrincipalType.valueOf(request.principalType()), request.principalId(),
                request.workspaceId(), request.organizationId());
        return authority.refund(new RefundPaymentCommand(principal, request.transactionId(),
                request.originalCaptureReference(), new Money(request.amountMinor(), request.currencyCode()),
                request.expectedVersion(), request.idempotencyKey(), "payment-api",
                request.reason(), request.traceId(), request.occurredAt()));
    }

    private static PrincipalRef principal(ConfirmPaymentRequest request) {
        return new PrincipalRef(request.tenantId(), PrincipalType.valueOf(request.principalType()),
                request.principalId(), request.workspaceId(), request.organizationId());
    }
}
