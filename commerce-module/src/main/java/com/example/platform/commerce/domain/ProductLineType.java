package com.example.platform.commerce.domain;

/**
 * Fulfillment semantics for a catalog line item.
 */
public enum ProductLineType {
    /** Recurring base plan; sets tenant tier and primary subscription contract. */
    BASE_SUBSCRIPTION,
    /** Recurring add-on; additional subscription contract + entitlement grant. */
    ADD_ON_SUBSCRIPTION,
    /** One-time or recurring credit top-up to tenant wallet. */
    CREDIT_PACK,
    /** Expands licensed seats / workspace pool quota for a feature. */
    SEAT_PACK
}
