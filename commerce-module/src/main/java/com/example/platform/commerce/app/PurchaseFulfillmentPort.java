package com.example.platform.commerce.app;

/**
 * Projects a confirmed purchase into billing contracts and entitlement state.
 * Implemented in platform-app; invoked from commerce checkout confirmation.
 */
public interface PurchaseFulfillmentPort {

    void fulfill(PurchaseFulfillmentCommand command);
}
