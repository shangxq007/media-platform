package com.example.platform.payment.api;

import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.infrastructure.PaymentWebhookAdapter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {
    private final PaymentWebhookAdapter webhookAdapter;

    public PaymentWebhookController(PaymentWebhookAdapter webhookAdapter) {
        this.webhookAdapter = webhookAdapter;
    }

    @PostMapping("/{providerCode}")
    public PaymentTransaction parse(@PathVariable String providerCode, @RequestHeader Map<String, String> headers, @RequestBody String body) {
        return webhookAdapter.handle(providerCode, headers, body);
    }
}
