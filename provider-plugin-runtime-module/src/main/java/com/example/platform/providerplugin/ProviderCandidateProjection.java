package com.example.platform.providerplugin;

import com.example.platform.execution.compatibility.ProviderCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Projects typed PF4J provider contributions into typed-chain Stage-1 candidates.
 *
 * <p>This is a pure mapping: each contribution becomes exactly one
 * {@link ProviderCandidate} carrying its own binding pin, descriptor, execution
 * contract, capability profile and {@link ProviderPluginContribution#providerStaticCompatibility()
 * static compatibility declaration}. Nothing is derived, defaulted or invented here.</p>
 *
 * <p>No provider selection policy exists in V1 and none is introduced here: the
 * projection returns every declared candidate in the caller's order (the
 * {@link ProviderPluginCatalog} order is canonical: plugin id, then plugin
 * version). Binding, ambiguity rejection and materialization decisions remain
 * with the Stage-1 feasibility view and the #22 binding entry.</p>
 *
 * <p>Locating this beside the catalog is deliberate: this module owns the
 * contribution type and already depends on the execution-planning module, so the
 * projection never forces the planning module to reach into provider-plugin or
 * worker-fabric internals.</p>
 */
public final class ProviderCandidateProjection {

    private ProviderCandidateProjection() {
    }

    /** Projects every contribution registered in the catalog, in catalog order. */
    public static List<ProviderCandidate> project(ProviderPluginCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        return project(catalog.contributions());
    }

    /** Projects the supplied contributions, preserving the supplied order. */
    public static List<ProviderCandidate> project(List<ProviderPluginContribution> contributions) {
        Objects.requireNonNull(contributions, "contributions");
        List<ProviderCandidate> candidates = new ArrayList<>(contributions.size());
        for (ProviderPluginContribution contribution : contributions) {
            Objects.requireNonNull(contribution, "contributions element");
            candidates.add(new ProviderCandidate(
                    contribution.providerBindingPin(),
                    contribution.providerDescriptor(),
                    contribution.providerExecutionContract(),
                    contribution.providerCapabilityProfile(),
                    contribution.providerStaticCompatibility()));
        }
        return List.copyOf(candidates);
    }
}
