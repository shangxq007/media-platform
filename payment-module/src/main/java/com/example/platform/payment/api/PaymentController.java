package com.example.platform.payment.api;

import com.example.platform.payment.api.dto.ConfirmPaymentRequest;
import com.example.platform.payment.api.dto.RefundPaymentRequest;
import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.RefundResult;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
        throw FailClosedAuthorization.unavailable("payment confirmation");
    }

    @PostMapping("/refunds")
    public RefundResult refund(@RequestBody RefundPaymentRequest request) {
        throw FailClosedAuthorization.unavailable("payment refund");
    }
}
