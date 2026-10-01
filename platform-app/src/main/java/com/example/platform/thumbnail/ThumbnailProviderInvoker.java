package com.example.platform.thumbnail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Worker-side <em>execution adapter</em> for {@code media.thumbnail}
 * (COVER-THUMBNAIL-REBUILD-001, action 2).
 *
 * <p>This is deliberately <b>not</b> a discovery or registration authority: the capability is
 * discovered through the platform capability registry
 * ({@link ThumbnailPlatformRegistration} -> {@code PluginRegistrationPort}). This adapter only
 * selects the single registered {@link ThumbnailCapabilityProvider} bean that declares the slice
 * capability and delegates execution, exactly as {@code CoverImageCapabilityRegistry} does behind
 * the cover platform registration. It replaces the retired slice-local
 * {@code ThumbnailCapabilityRegistry}; there is no second discovery path and no hardcoded provider
 * pin other than the capability's own family constant.
 *
 * <p>Fail-closed composition: empty registration, a duplicate provider family, a provider whose
 * family and implementation identities collapse into one value, a provider that does not declare
 * {@code media.thumbnail}, and an ambiguous multi-provider set all fail at construction.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class ThumbnailProviderInvoker {

    private final Map<String, ThumbnailCapabilityProvider> providers;

    public ThumbnailProviderInvoker(List<ThumbnailCapabilityProvider> registered) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalStateException("no thumbnail provider registered");
        }
        this.providers = new HashMap<>();
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
    public static ThumbnailProviderInvoker of(ThumbnailCapabilityProvider provider) {
        return new ThumbnailProviderInvoker(List.of(provider));
    }

    public ThumbnailCapabilityProvider provider() {
        return providers.get(ThumbnailContracts.PROVIDER);
    }

    public ThumbnailCapabilityProvider provider(String providerFamilyId) {
        var provider = providers.get(providerFamilyId);
        if (provider == null) throw new IllegalArgumentException("unregistered thumbnail provider: " + providerFamilyId);
        return provider;
    }

    public ThumbnailCapabilityProvider.Result invoke(String capabilityId,
            ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return provider().extract(capabilityId, request, input, cancelled);
    }

    public ThumbnailCapabilityProvider.Result invoke(
            ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return invoke(ThumbnailContracts.CAPABILITY, request, input, cancelled);
    }
}
