package com.example.platform.delivery.app;

import com.example.platform.render.api.event.RenderJobCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryCompletionListener {

    private final DeliveryJobService deliveryJobService;

    public DeliveryCompletionListener(DeliveryJobService deliveryJobService) {
        this.deliveryJobService = deliveryJobService;
    }

    @EventListener
    public void onRenderJobCompleted(RenderJobCompletedEvent event) {
        deliveryJobService.onRenderJobCompleted(event);
    }
}
