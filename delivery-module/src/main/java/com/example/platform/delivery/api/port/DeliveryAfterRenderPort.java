package com.example.platform.delivery.api.port;

/**
 * Coordinates outbound delivery after a render job completes (Temporal activity / tests).
 * {@code RenderJobCompletedEvent}, {@code DeliveryCompletionListener}, and
 * {@code onRenderJobCompleted} apply AUTO policies and enqueue Delivery-owned rows.
 */
public interface DeliveryAfterRenderPort {

    /**
     * Processes already-enqueued QUEUED Delivery-owned rows for this render only.
     *
     * @return number of delivery jobs processed in this call
     */
    int finalizeDeliveriesForRenderJob(String renderJobId);
}
