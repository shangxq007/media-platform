package com.example.platform.workerfabric.domain;

/**
 * Dispatcher-to-transport delivery seam supporting at-least-once delivery.
 *
 * <p>A successful receipt means only that delivery was accepted. It conveys no execution-state or
 * completion authority.
 */
@FunctionalInterface
public interface DurableDeliveryPort<M> {

    DeliveryReceipt deliver(M message);

    record DeliveryReceipt(String transportReference) {

        public DeliveryReceipt {
            if (transportReference == null || transportReference.isBlank()) {
                throw new IllegalArgumentException("transportReference must not be blank");
            }
        }
    }
}
