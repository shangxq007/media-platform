package com.example.platform.render.app.event;
import com.example.platform.render.api.event.*;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.outbox.app.OutboxEventService;
import org.springframework.stereotype.Service;
/** Typed Render append boundary. Only the Outbox dispatcher delivers these facts to listeners. */
@Service
public class RenderLifecyclePublisher {
 private final OutboxEventService outbox;
 public RenderLifecyclePublisher(OutboxEventService outbox){this.outbox=java.util.Objects.requireNonNull(outbox);}
 public void publishEvent(RenderJobCreatedEvent e){outbox.append(RenderOutboxEvents.RENDERJOBCREATEDEVENT.append(e.tenantId(),e,e.factKey()));}
 public void publishEvent(RenderJobStatusChangedEvent e){outbox.append(RenderOutboxEvents.RENDERJOBSTATUSCHANGEDEVENT.append(e.tenantId(),e,e.factKey()));}
 public void publishEvent(RenderJobCompletedEvent e){outbox.append(RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT.append(e.tenantId(),e,e.factKey()));}
 public void publishEvent(RenderJobFailedEvent e){outbox.append(RenderOutboxEvents.RENDERJOBFAILEDEVENT.append(e.tenantId(),e,e.factKey()));}
 public void publishEvent(RenderCacheHashInvalidatedEvent e){outbox.append(RenderOutboxEvents.CACHE_INVALIDATED.append(e.tenantId(),e,e.factKey()));}
 public void publishEvent(ArtifactCreatedEvent e){outbox.append(RenderOutboxEvents.ARTIFACTCREATEDEVENT.append(com.example.platform.shared.web.TenantGuard.requireTenantId(),e,"render-artifact:"+e.artifactId()));}
}
