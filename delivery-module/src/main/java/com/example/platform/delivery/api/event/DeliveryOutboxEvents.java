package com.example.platform.delivery.api.event;
import com.example.platform.outbox.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public final class DeliveryOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<DeliveryCompletedEvent> COMPLETED=new OutboxEventType<>("delivery.completed",1,"delivery_job",DeliveryCompletedEvent.class,DeliveryCompletedEvent::deliveryJobId,DeliveryCompletedEvent::tenantId);
    public static final OutboxEventType<DeliveryFailedEvent> FAILED=new OutboxEventType<>("delivery.failed",1,"delivery_job",DeliveryFailedEvent.class,DeliveryFailedEvent::deliveryJobId,DeliveryFailedEvent::tenantId);
    public List<OutboxEventType<?>> types(){return List.of(COMPLETED,FAILED);}
}
