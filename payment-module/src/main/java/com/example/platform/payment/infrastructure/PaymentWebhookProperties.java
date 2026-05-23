package com.example.platform.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.payment.webhook")
public class PaymentWebhookProperties {

    /**
     * When false (default), webhooks with invalid or missing signatures are rejected.
     * Dev/test may set true for Noop providers.
     */
    private boolean allowUnsigned;

    public boolean isAllowUnsigned() {
        return allowUnsigned;
    }

    public void setAllowUnsigned(boolean allowUnsigned) {
        this.allowUnsigned = allowUnsigned;
    }
}
