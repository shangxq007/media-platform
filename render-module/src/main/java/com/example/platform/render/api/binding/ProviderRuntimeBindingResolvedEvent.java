package com.example.platform.render.api.binding;
import com.example.platform.shared.usage.ProviderRef;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
/** Actual keyed Render-provider selection by the existing runtime engine.
 * Does not claim an unavailable native execution pin, provider success, or job completion. */
public record ProviderRuntimeBindingResolvedEvent(String renderJobId,String projectId,String tenantId,
        ProviderRef provider,String resolutionId,Instant resolvedAt) {
 public ProviderRuntimeBindingResolvedEvent {
    for(String value:new String[]{renderJobId,projectId,tenantId,resolutionId})if(value==null||value.isBlank())throw new IllegalArgumentException("binding scope required");
    Objects.requireNonNull(provider);Objects.requireNonNull(resolvedAt);
 }
 public String factKey(){return "provider.binding:"+UUID.nameUUIDFromBytes((tenantId+"\0"+projectId+"\0"+renderJobId+"\0"+resolutionId).getBytes(StandardCharsets.UTF_8));}
}
