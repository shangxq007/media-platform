package com.example.platform.execution.domain.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable declared execution-feasibility projection for a provider implementation.
 * It is not a CapabilityRegistry and owns no capability definition or lifecycle.
 */
public record ProviderCapabilityProfile(
        ProviderCapabilityProfileVersionOrDigest reference,
        List<ProviderCapabilitySupport> supportDeclarations) {

    private static final Comparator<ProviderCapabilitySupport> SUPPORT_ORDER =
            Comparator.comparing((ProviderCapabilitySupport value) -> value.capabilityId().value())
                    .thenComparingInt(value -> value.contractVersionRange().min().major())
                    .thenComparingInt(value -> value.contractVersionRange().min().minor())
                    .thenComparingInt(value -> value.contractVersionRange().max().major())
                    .thenComparingInt(value -> value.contractVersionRange().max().minor())
                    .thenComparing(value -> value.capabilityImplementationPin()
                            .map(pin -> "1" + pin.value()).orElse("0"));

    public ProviderCapabilityProfile {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(supportDeclarations, "supportDeclarations");
        var canonical = new ArrayList<ProviderCapabilitySupport>(supportDeclarations.size());
        for (ProviderCapabilitySupport support : supportDeclarations) {
            canonical.add(Objects.requireNonNull(support, "supportDeclarations element"));
        }
        if (new HashSet<>(canonical).size() != canonical.size()) {
            throw new IllegalArgumentException("duplicate provider capability support declaration");
        }
        canonical.sort(SUPPORT_ORDER);
        supportDeclarations = List.copyOf(canonical);
    }
}
