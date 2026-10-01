package com.example.platform.thumbnail;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runtime composition point for the one registered media.thumbnail capability.
 *
 * <p>The pinned provider is a capability-independent provider <em>family</em> identity
 * ({@link ThumbnailContracts#PROVIDER}); the manifest carries the implementation identity in its own
 * {@code providerImplementationId} slot. Registration fails closed if a provider repeats the family
 * id in the implementation slot — the exact "implementation id in the provider-id position" defect
 * this composition replaces — so the two identities can never silently collapse again.
 */
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
            if (!manifest.supports(ThumbnailContracts.CAPABILITY)) continue;
            if (manifest.providerId().equals(manifest.providerImplementationId()))
                throw new IllegalStateException("thumbnail provider family and implementation identity"
                        + " must differ: " + manifest.providerId());
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
        return invoke(ThumbnailContracts.CAPABILITY, request, input, cancelled);
    }
    public ThumbnailCapabilityProvider.Result invoke(String capabilityId, ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return provider(ThumbnailContracts.PROVIDER).extract(capabilityId, request, input, cancelled);
    }
}
