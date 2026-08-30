package com.example.platform.commerce.domain;

import com.example.platform.shared.commercial.Money;

public record CheckoutSession(String checkoutSessionId, String tenantId, String canonicalProductCode,
        String productId, String offeringId, long offeringVersion, AuthorityReference commercialPriceReference,
        Money amountSnapshot, String redirectUrl, String providerHint) {}
