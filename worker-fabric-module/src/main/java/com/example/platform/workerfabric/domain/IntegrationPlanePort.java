package com.example.platform.workerfabric.domain;

/**
 * Outbound integration-mechanics boundary.
 *
 * <p>The authoritative application transaction records an {@link OutboxDeliveryIntent} through the
 * repository's existing outbox mechanics. Only a dispatcher-delivered intent reaches this port.
 * Implementations may call an external backend, but have no canonical database mutation contract.
 */
@FunctionalInterface
public interface IntegrationPlanePort<I, R> {

    R integrate(I durablyDeliveredIntent);
}
