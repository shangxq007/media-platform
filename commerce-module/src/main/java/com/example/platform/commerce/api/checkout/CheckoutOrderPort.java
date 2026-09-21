package com.example.platform.commerce.api.checkout;

import com.example.platform.commerce.domain.PurchaseOrderCreatedEvent;

/** Commerce's existing order confirmation boundary for a durable payment handoff.
 * The owner resolves the persisted checkout principal; a consumer cannot override it.
 */
public interface CheckoutOrderPort {
    PurchaseOrderCreatedEvent confirmCheckout(String checkoutSessionId);
}
