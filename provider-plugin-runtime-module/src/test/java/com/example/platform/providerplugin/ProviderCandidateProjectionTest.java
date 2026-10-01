package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The catalog → candidate projection: a pure mapping of typed PF4J contributions
 * into typed-chain Stage-1 candidates, with no derivation and no selection policy.
 */
class ProviderCandidateProjectionTest {

    @Test
    void emptyCatalogProjectsNoCandidates() {
        assertThat(ProviderCandidateProjection.project(new ProviderPluginCatalog())).isEmpty();
        assertThat(ProviderCandidateProjection.project(List.of())).isEmpty();
    }

    @Test
    void everyRegisteredContributionBecomesExactlyOneCandidate() {
        ProviderPluginCatalog catalog = new ProviderPluginCatalog();
        ProviderPluginContribution alpha = contribution("alpha");
        ProviderPluginContribution beta = contribution("beta");
        catalog.register(alpha);
        catalog.register(beta);

        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(catalog);

        assertThat(candidates).hasSize(2);
        assertThat(candidates).extracting(candidate -> candidate.bindingPin().providerId().value())
                .containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void candidateCarriesTheContributionMetadataVerbatim() {
        ProviderPluginContribution contribution = contribution("alpha");

        ProviderCandidate candidate = ProviderCandidateProjection.project(List.of(contribution)).getFirst();

        assertThat(candidate.bindingPin()).isEqualTo(contribution.providerBindingPin());
        assertThat(candidate.descriptor()).isEqualTo(contribution.providerDescriptor());
        assertThat(candidate.executionContract()).isEqualTo(contribution.providerExecutionContract());
        assertThat(candidate.capabilityProfile()).isEqualTo(contribution.providerCapabilityProfile());
    }

    @Test
    void undeclaredContributionProjectsAnUnknownFailClosedDeclaration() {
        ProviderCandidate candidate =
                ProviderCandidateProjection.project(List.of(contribution("alpha"))).getFirst();

        assertThat(candidate.staticCompatibility()).isEqualTo(ProviderStaticCompatibility.unknown());
        assertThat(candidate.staticCompatibility().knowledge())
                .isEqualTo(ProviderStaticCompatibility.Knowledge.UNKNOWN);
        assertThat(candidate.staticCompatibility().loweringSupport())
                .isEqualTo(ProviderStaticCompatibility.LoweringSupport.UNKNOWN);
    }

    @Test
    void declaredCompatibilityIsProjectedVerbatim() {
        // TEST-ONLY double standing in for a provider-declared compatibility value
        // (concrete values are built by the provider modules that can see the
        // render-owned component types).
        ProviderStaticCompatibility declared = mock(ProviderStaticCompatibility.class);

        ProviderCandidate candidate =
                ProviderCandidateProjection.project(List.of(contribution("alpha", declared))).getFirst();

        assertThat(candidate.staticCompatibility()).isSameAs(declared);
    }

    @Test
    void projectionPreservesTheCandidateOrderItWasGiven() {
        ProviderPluginCatalog catalog = new ProviderPluginCatalog();
        catalog.register(contribution("beta"));
        catalog.register(contribution("alpha"));

        List<String> catalogOrder = ProviderCandidateProjection.project(catalog).stream()
                .map(candidate -> candidate.bindingPin().providerId().value())
                .toList();
        List<String> suppliedOrder = ProviderCandidateProjection
                .project(List.of(contribution("beta"), contribution("alpha"))).stream()
                .map(candidate -> candidate.bindingPin().providerId().value())
                .toList();

        assertThat(suppliedOrder).containsExactly("beta", "alpha");
        // the catalog exposes a canonical order (pluginId, then pluginVersion)
        assertThat(catalogOrder).containsExactly("alpha", "beta");
    }

    @Test
    void projectionRejectsNullInputs() {
        assertThatThrownBy(() -> ProviderCandidateProjection.project((ProviderPluginCatalog) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProviderCandidateProjection.project((List<ProviderPluginContribution>) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProviderCandidateProjection.project(Arrays.asList(contribution("alpha"), null)))
                .isInstanceOf(NullPointerException.class);
    }

    /** TEST-ONLY contribution double (real metadata records; unrelated members unused). */
    private static ProviderPluginContribution contribution(String provider) {
        return contribution(provider, null);
    }

    private static ProviderPluginContribution contribution(
            String provider, ProviderStaticCompatibility declaration) {
        ProviderBindingPin bindingPin = ProviderCatalogTestFixture.pin(provider);
        return new ProviderPluginContribution() {
            @Override public String pluginId() { return provider + ".plugin"; }
            @Override public String pluginVersion() { return "1.0.0"; }
            @Override public PluginDescriptor pluginDescriptor() { return null; }
            @Override public com.example.platform.execution.domain.provider.ProviderDescriptor
                    providerDescriptor() {
                return ProviderCatalogTestFixture.descriptor(provider);
            }
            @Override public com.example.platform.execution.domain.provider.ProviderExecutionContract
                    providerExecutionContract() {
                return ProviderCatalogTestFixture.executionContract();
            }
            @Override public com.example.platform.execution.domain.provider.ProviderCapabilityProfile
                    providerCapabilityProfile() {
                return ProviderCatalogTestFixture.capabilityProfile();
            }
            @Override public ProviderStaticCompatibility providerStaticCompatibility() {
                return declaration != null ? declaration : ProviderPluginContribution.super.providerStaticCompatibility();
            }
            @Override public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() { return null; }
            @Override public ProviderBindingPin providerBindingPin() { return bindingPin; }
            @Override public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
                    ProviderPluginRuntimeContext context) { return null; }
        };
    }
}
