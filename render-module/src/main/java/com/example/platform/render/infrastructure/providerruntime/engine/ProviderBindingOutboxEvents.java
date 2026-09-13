package com.example.platform.render.infrastructure.providerruntime.engine;
import com.example.platform.render.api.binding.ProviderRuntimeBindingResolvedEvent;
import com.example.platform.outbox.api.event.*;
import java.util.List;
import org.springframework.stereotype.Component;
/** Binding-owner catalog, separate from generic Render lifecycle semantics. */
@Component
public class ProviderBindingOutboxEvents implements OutboxEventCatalog {
 public static final OutboxEventType<ProviderRuntimeBindingResolvedEvent> RESOLVED=new OutboxEventType<>("render.provider.binding.resolved",1,"render_job",ProviderRuntimeBindingResolvedEvent.class,ProviderRuntimeBindingResolvedEvent::renderJobId,ProviderRuntimeBindingResolvedEvent::tenantId);
 public List<OutboxEventType<?>> types(){return List.of(RESOLVED);}
}
