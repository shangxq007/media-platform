package com.example.platform.providerplugin;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.binding.ProviderCandidateCanonicalCodec;
import java.util.ArrayList;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
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
 * <p>The
 * projection returns every declared candidate in the caller's order (the
 * {@link ProviderPluginCatalog} order is canonical: plugin id, then plugin
 * version). Binding, complete-plan selection and materialization decisions remain
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

    /** Compare the actual Worker contribution with the complete declaration recorded by planning. */
    public static void requireMatching(ProviderPluginContribution contribution, ProviderCandidate recorded) {
        var actual = project(List.of(contribution)).getFirst();
        if (!java.util.Arrays.equals(ProviderCandidateCanonicalCodec.encode(actual),
                ProviderCandidateCanonicalCodec.encode(recorded))) {
            throw new IllegalArgumentException("Worker provider declaration differs from immutable binding inputs");
        }
    }

    /** Projects the supplied contributions, preserving the supplied order. */
    public static List<ProviderCandidate> project(List<ProviderPluginContribution> contributions) {
        Objects.requireNonNull(contributions, "contributions");
        List<ProviderCandidate> candidates = new ArrayList<>(contributions.size());
        for (ProviderPluginContribution contribution : contributions) {
            Objects.requireNonNull(contribution, "contributions element");
            var candidate = new ProviderCandidate(
                    contribution.providerBindingPin(),
                    contribution.providerDescriptor(),
                    contribution.providerExecutionContract(),
                    contribution.providerCapabilityProfile(),
                    contribution.providerStaticCompatibility());
            var reference = candidate.bindingPin().providerCapabilityProfileVersionOrDigest();
            if (reference instanceof ProviderCapabilityProfileVersionOrDigest.DigestReference) {
                var nativeContent = contribution.implementationContent().orElseThrow(() ->
                        new IllegalArgumentException("Digest-pinned provider has no native implementation identity"));
                var declaration = ProviderCandidateCanonicalCodec.effectiveDeclaration(candidate.executionContract(),
                        candidate.capabilityProfile().supportDeclarations(), candidate.staticCompatibility());
                if (!reference.equals(nativeContent.profile(declaration))
                        || !reference.equals(candidate.capabilityProfile().reference())
                        || !reference.equals(candidate.descriptor().providerCapabilityProfileReference())) {
                    throw new IllegalArgumentException("Effective declaration differs from immutable binding inputs");
                }
            }
            candidates.add(candidate);
        }
        return List.copyOf(candidates);
    }
}
