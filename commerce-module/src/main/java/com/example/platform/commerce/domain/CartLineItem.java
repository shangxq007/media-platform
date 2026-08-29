package com.example.platform.commerce.domain;

public record CartLineItem(String productCode, int quantity, String productId, String offeringId,
        long offeringVersion, AuthorityReference commercialPriceReference,
        long amountMinorSnapshot, String currencyCodeSnapshot) {

    public CartLineItem {
        if (quantity < 1) {
            quantity = 1;
        }
    }
}
