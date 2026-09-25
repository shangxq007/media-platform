package com.example.platform.coverimage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runtime composition point for the media.cover-image capability.
 *
 * <p>Model: capability → provider resolution over every registered provider's <em>declared
 * capability list</em>. A provider that serves several capabilities is indexed under each of them —
 * it is never skipped for a capability it declares — and one capability may be served by several
 * providers. This mirrors the platform capability-registry shape (declared capability list +
 * capability-keyed lookup) without registering the slice in the platform provider contract.
 *
 * <p>Fail-closed: empty registration, a duplicate provider identity, a deployment pin that names no
 * registered provider (or a provider that does not declare the slice capability), a capability with
 * no provider, and an ambiguous capability without a deployment pin all fail. There is no fallback
 * provider and no implicit default provider.
 *
 * <p>Provider selection is <em>deployment configuration</em>
 * ({@code app.cover-image.pinned-provider}), never a code constant: the registry holds no hardcoded
 * provider identity.
 */
@Component
public final class CoverImageCapabilityRegistry {

    private final Map<String, CoverImageCapabilityProvider> byProviderId;
    private final Map<String, List<CoverImageCapabilityProvider>> byCapability;
    private final String pinnedProviderId;

    @Autowired
    public CoverImageCapabilityRegistry(
            List<CoverImageCapabilityProvider> registered,
            @Value("${app.cover-image.pinned-provider:}") String pinnedProviderId) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalStateException("no cover-image provider registered");
        }
        Map<String, CoverImageCapabilityProvider> accepted = new LinkedHashMap<>();
        Map<String, List<CoverImageCapabilityProvider>> index = new LinkedHashMap<>();
        for (CoverImageCapabilityProvider provider : registered) {
            Objects.requireNonNull(provider, "provider");
            CoverImageCapabilityProvider.Manifest manifest =
                    Objects.requireNonNull(provider.manifest(), "provider manifest");
            if (accepted.putIfAbsent(manifest.providerId(), provider) != null) {
                throw new IllegalStateException(
                        "duplicate cover-image provider: " + manifest.providerId());
            }
            for (CoverImageCapabilityProvider.CapabilityDeclaration capability
                    : manifest.capabilities()) {
                index.computeIfAbsent(capability.capabilityId(), key -> new ArrayList<>())
                        .add(provider);
            }
        }
        String pin = pinnedProviderId == null ? "" : pinnedProviderId.strip();
        if (!pin.isEmpty() && !accepted.containsKey(pin)) {
            throw new IllegalStateException("pinned cover-image provider is not registered: " + pin);
        }
        List<CoverImageCapabilityProvider> sliceCapabilityProviders =
                index.get(CoverImageContracts.CAPABILITY);
        if (sliceCapabilityProviders == null || sliceCapabilityProviders.isEmpty()) {
            throw new IllegalStateException("no registered provider declares capability "
                    + CoverImageContracts.CAPABILITY);
        }
        if (sliceCapabilityProviders.size() > 1 && pin.isEmpty()) {
            throw new IllegalStateException("capability " + CoverImageContracts.CAPABILITY
                    + " is served by multiple providers; set app.cover-image.pinned-provider");
        }
        if (!pin.isEmpty() && !accepted.get(pin).manifest().supports(CoverImageContracts.CAPABILITY)) {
            throw new IllegalStateException("pinned cover-image provider " + pin
                    + " does not declare capability " + CoverImageContracts.CAPABILITY);
        }
        Map<String, List<CoverImageCapabilityProvider>> canonical = new LinkedHashMap<>();
        index.forEach((capabilityId, providers) -> canonical.put(capabilityId, List.copyOf(providers)));
        this.byProviderId = Map.copyOf(accepted);
        this.byCapability = Map.copyOf(canonical);
        this.pinnedProviderId = pin;
    }

    /** Test/embedded composition without a deployment pin. */
    public CoverImageCapabilityRegistry(List<CoverImageCapabilityProvider> registered) {
        this(registered, "");
    }

    /**
     * Deterministic capability → provider resolution: the pinned provider when it declares the
     * capability, otherwise the single declaring provider. Fail closed, no fallback provider.
     */
    public CoverImageCapabilityProvider provider(String capabilityId) {
        List<CoverImageCapabilityProvider> candidates = byCapability.get(capabilityId);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException(
                    "no registered provider serves capability " + capabilityId);
        }
        List<CoverImageCapabilityProvider> ordered = candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.manifest().providerId()))
                .toList();
        if (!pinnedProviderId.isEmpty()) {
            for (CoverImageCapabilityProvider candidate : ordered) {
                if (pinnedProviderId.equals(candidate.manifest().providerId())) {
                    return candidate;
                }
            }
        }
        if (ordered.size() == 1) {
            return ordered.get(0);
        }
        throw new IllegalStateException("capability " + capabilityId
                + " is served by multiple providers and none matches the pinned provider '"
                + pinnedProviderId + "'");
    }

    /** Lookup by provider (family) identity, independent of any capability. */
    public Optional<CoverImageCapabilityProvider> providerByProviderId(String providerId) {
        return Optional.ofNullable(byProviderId.get(providerId));
    }

    /** Declared capability ids across every registered provider (deterministic). */
    public Set<String> capabilities() {
        return Set.copyOf(new TreeSet<>(byCapability.keySet()));
    }

    /** Registered provider identities (deterministic). */
    public Set<String> providerIds() {
        return Set.copyOf(new TreeSet<>(byProviderId.keySet()));
    }

    /** Number of registered providers. */
    public int size() {
        return byProviderId.size();
    }

    public CoverImageCapabilityProvider.Result invoke(
            String capabilityId,
            CoverImageContracts.Request request,
            Path inputPath,
            BooleanSupplier cancelled) {
        return provider(capabilityId).render(capabilityId, request, inputPath, cancelled);
    }
}
