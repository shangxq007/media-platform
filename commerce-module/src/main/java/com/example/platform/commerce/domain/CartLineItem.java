package com.example.platform.commerce.domain;

public record CartLineItem(String productCode, int quantity) {

    public CartLineItem {
        if (quantity < 1) {
            quantity = 1;
        }
    }
}
