package com.example.platform.render.infrastructure.providerruntime.engine;
import com.example.platform.render.api.binding.ProviderRuntimeBindingResolvedEvent;
import com.example.platform.outbox.app.OutboxEventService;
import org.springframework.stereotype.Service;
@Service
public class ProviderBindingPublisher {
 private final OutboxEventService outbox;
 public ProviderBindingPublisher(OutboxEventService outbox){this.outbox=outbox;}
 public void resolved(ProviderRuntimeBindingResolvedEvent event){outbox.append(ProviderBindingOutboxEvents.RESOLVED.append(event.tenantId(),event,event.factKey()));}
}
