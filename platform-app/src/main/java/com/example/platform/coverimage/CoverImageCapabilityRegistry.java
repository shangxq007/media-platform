package com.example.platform.coverimage;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/**
 * Runtime composition point for the one registered media.cover-image capability.
 *
 * <p>Fails closed: the pinned provider must be registered and two providers may not claim one id.
 * There is no fallback provider and no implicit default provider.
 */
@Component
public final class CoverImageCapabilityRegistry {

    private final Map<String, CoverImageCapabilityProvider> providers;

    public CoverImageCapabilityRegistry(List<CoverImageCapabilityProvider> registered) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalStateException("no cover-image provider registered");
        }
        Map<String, CoverImageCapabilityProvider> accepted = new LinkedHashMap<>();
        for (CoverImageCapabilityProvider provider : registered) {
            Objects.requireNonNull(provider, "provider");
            var manifest = provider.manifest();
            Objects.requireNonNull(manifest, "provider manifest");
            if (!CoverImageContracts.CAPABILITY.equals(manifest.capabilityId())) {
                continue;
            }
            if (accepted.putIfAbsent(manifest.providerId(), provider) != null) {
                throw new IllegalStateException(
                        "duplicate cover-image provider: " + manifest.providerId());
            }
        }
        if (!accepted.containsKey(CoverImageContracts.PROVIDER)) {
            throw new IllegalStateException("pinned media.cover-image provider is not registered");
        }
        this.providers = Map.copyOf(accepted);
    }

    public CoverImageCapabilityProvider provider() {
        return providers.get(CoverImageContracts.PROVIDER);
    }

    public CoverImageCapabilityProvider provider(String providerId) {
        CoverImageCapabilityProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("unregistered cover-image provider: " + providerId);
        }
        return provider;
    }

    public int size() {
        return providers.size();
    }

    public CoverImageCapabilityProvider.Result invoke(
            CoverImageContracts.Request request, Path inputPath, BooleanSupplier cancelled) {
        return provider().render(request, inputPath, cancelled);
    }
}
