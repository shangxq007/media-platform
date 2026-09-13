package com.example.platform.audit.api.event;
import com.example.platform.outbox.api.event.*;
import java.util.List;
@org.springframework.stereotype.Component
public final class AuditOutboxEvents implements OutboxEventCatalog {
    public static final OutboxEventType<UsageAnomalyDetectedEvent> ANOMALY=new OutboxEventType<>("audit.usage.anomaly.detected",1,"usage_anomaly",UsageAnomalyDetectedEvent.class,UsageAnomalyDetectedEvent::eventId,UsageAnomalyDetectedEvent::tenantId);
    public List<OutboxEventType<?>> types(){return List.of(ANOMALY);}
}
