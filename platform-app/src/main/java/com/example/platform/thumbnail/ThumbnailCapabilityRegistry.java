package com.example.platform.thumbnail;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runtime composition point for the one registered media.thumbnail capability. */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class ThumbnailCapabilityRegistry {
    private final java.util.Map<String, ThumbnailCapabilityProvider> providers;
    public ThumbnailCapabilityRegistry(java.util.List<ThumbnailCapabilityProvider> registered) {
        if (registered == null || registered.isEmpty()) throw new IllegalStateException("no thumbnail provider registered");
        this.providers = new java.util.HashMap<>();
        for (var provider : registered) {
            Objects.requireNonNull(provider, "provider");
            var manifest = provider.manifest();
            if (!ThumbnailContracts.CAPABILITY.equals(manifest.capabilityId())) continue;
            if (providers.putIfAbsent(manifest.providerId(), provider) != null)
                throw new IllegalStateException("duplicate thumbnail provider: " + manifest.providerId());
        }
        if (!providers.containsKey(ThumbnailContracts.PROVIDER))
            throw new IllegalStateException("pinned media.thumbnail provider is not registered");
    }
    /**
     * Convenience entry point for non-Spring callers that hold exactly one provider.
     * Deliberately a static factory: the class must expose a single public constructor so
     * Spring can select it implicitly, without {@code @Autowired}.
     */
    public static ThumbnailCapabilityRegistry of(ThumbnailCapabilityProvider provider) {
        return new ThumbnailCapabilityRegistry(java.util.List.of(provider));
    }
    public ThumbnailCapabilityProvider provider() { return providers.get(ThumbnailContracts.PROVIDER); }
    public ThumbnailCapabilityProvider provider(String providerId) {
        var provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("unregistered thumbnail provider: " + providerId);
        return provider;
    }
    public ThumbnailCapabilityProvider.Result invoke(ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return provider(ThumbnailContracts.PROVIDER).extract(request, input, cancelled);
    }
}
