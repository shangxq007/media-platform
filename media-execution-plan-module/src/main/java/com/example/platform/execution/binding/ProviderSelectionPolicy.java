package com.example.platform.execution.binding;

import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.planning.CanonicalWriter;
import java.util.Objects;
import java.util.Optional;

/** Bounded homogeneous binding. A restriction applies to the entire plan. */
public record ProviderSelectionPolicy(Optional<ProviderId> requiredProvider) {
    public static final String VERSION = "bmf-preferred-homogeneous-v1";
    public static final ProviderSelectionPolicy PREFERRED = new ProviderSelectionPolicy(Optional.empty());

    public ProviderSelectionPolicy {
        Objects.requireNonNull(requiredProvider, "requiredProvider");
    }

    public static ProviderSelectionPolicy restrictedTo(String id) {
        return new ProviderSelectionPolicy(Optional.of(ProviderId.of(id)));
    }

    public String canonical() {
        return new CanonicalWriter().tag(VERSION)
                .optional(requiredProvider.isPresent(), requiredProvider.map(ProviderId::value).orElse(null)).build();
    }

    static ProviderSelectionPolicy read(FrameReader reader) {
        reader.requireTag(VERSION);
        String id = reader.optional();
        if (reader.hasRemaining()) throw new UnsupportedPersistedConstructException("trailing selection policy");
        return id == null ? PREFERRED : restrictedTo(id);
    }
}
