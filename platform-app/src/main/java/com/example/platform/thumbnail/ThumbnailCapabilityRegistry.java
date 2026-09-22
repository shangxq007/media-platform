package com.example.platform.thumbnail;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/** Runtime composition point for the one registered media.thumbnail capability. */
@Component
public final class ThumbnailCapabilityRegistry {
    private final ThumbnailCapabilityProvider provider;
    public ThumbnailCapabilityRegistry(ThumbnailCapabilityProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        if (!ThumbnailContracts.CAPABILITY.equals(provider.manifest().capabilityId())
                || !ThumbnailContracts.PROVIDER.equals(provider.manifest().providerId())) {
            throw new IllegalStateException("media.thumbnail provider registration is not pinned");
        }
    }
    public ThumbnailCapabilityProvider provider() { return provider; }
    public ThumbnailCapabilityProvider.Result invoke(ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return provider.extract(request, input, cancelled);
    }
}
