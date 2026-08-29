package com.example.platform.payment.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    StripePaymentProperties.class,
    HyperswitchPaymentProperties.class,
    PaymentWebhookProperties.class,
    PaymentRoutingProperties.class
})
public class PaymentConfiguration {
}
