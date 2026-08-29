package com.example.platform.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit configuration-owned provider selection used when Commerce has no persisted mapping yet. */
@ConfigurationProperties(prefix = "platform.payment.routing")
public class PaymentRoutingProperties {
    private String defaultProviderCode = "stripe";

    public String getDefaultProviderCode() { return defaultProviderCode; }
    public void setDefaultProviderCode(String defaultProviderCode) {
        if (defaultProviderCode == null || defaultProviderCode.isBlank()) {
            throw new IllegalArgumentException("defaultProviderCode is required");
        }
        this.defaultProviderCode = defaultProviderCode;
    }
}
