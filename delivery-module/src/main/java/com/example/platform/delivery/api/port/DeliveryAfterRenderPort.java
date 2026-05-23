package com.example.platform.delivery.api.port;

/**
 * Enqueues and optionally runs outbound delivery after a render job completes (Temporal activity / tests).
 */
public interface DeliveryAfterRenderPort {

    /**
     * Applies AUTO delivery policies for the render job and processes queued delivery jobs.
     *
     * @return number of delivery jobs processed in this call
     */
    int finalizeDeliveriesForRenderJob(String renderJobId);
}
