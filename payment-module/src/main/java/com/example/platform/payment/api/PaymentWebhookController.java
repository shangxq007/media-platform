package com.example.platform.payment.api;

import com.example.platform.payment.app.PaymentGatewayService;
import com.example.platform.payment.domain.WebhookParseResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/payments")
public class PaymentWebhookController {
    private final PaymentGatewayService paymentGatewayService;

    public PaymentWebhookController(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    @PostMapping("/{providerCode}")
    public WebhookParseResult parse(@PathVariable String providerCode, @RequestHeader Map<String, String> headers, @RequestBody String body) {
        return paymentGatewayService.parseWebhook(providerCode, headers, body);
    }
}
