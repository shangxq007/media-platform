package com.example.platform.artifact.api.event;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.web.TenantGuard;
@org.springframework.stereotype.Service
public class ArtifactMetadataEventPublisher {
    private final OutboxEventService outbox;
    public ArtifactMetadataEventPublisher(OutboxEventService outbox) { this.outbox=outbox; }
    public void publish(AssetEnrichedEvent event) {
        TenantGuard.assertSameTenant(event.tenantId());
        outbox.append(ArtifactOutboxEvents.ASSETENRICHEDEVENT.append(TenantGuard.requireTenantId(),event,event.factKey()));
    }
}
