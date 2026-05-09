package com.example.platform.payment.api;

import com.example.platform.payment.api.dto.ConfirmPaymentRequest;
import com.example.platform.payment.app.PaymentGatewayService;
import com.example.platform.payment.domain.PaymentVerificationResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentGatewayService paymentGatewayService;

    public PaymentController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @PostMapping("/confirm")
    public PaymentVerificationResult confirm(@RequestBody ConfirmPaymentRequest request) {
        return paymentGatewayService.confirm(request.providerCode(), request.providerReference(), request.payload());
    }
}
