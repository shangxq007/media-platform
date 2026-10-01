package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import org.junit.jupiter.api.Test;

/**
 * The Stage-1 static compatibility declaration mechanism on
 * {@link ProviderPluginContribution}: a contributor that declares nothing stays
 * fail-closed, and a contributor may declare its own non-capability support facts.
 *
 * <p>Note: the concrete {@code ProviderStaticCompatibility} record names a
 * render-owned determinism enum that is not on this module's compile classpath
 * (the record is consumed through the execution-planning module). The mechanism is
 * therefore verified at the mechanism boundary — the default is the real
 * fail-closed {@code unknown()}, and an override is forwarded verbatim — while
 * concrete declared values are exercised by the provider modules that own them
 * (P2-4b/c).
 */
class ProviderPluginCompatibilityDeclarationTest {

    @Test
    void undeclaredContributionFailsClosedWithTheUnknownDeclaration() {
        ProviderPluginContribution contribution = contribution(null);

        ProviderStaticCompatibility declaration = contribution.providerStaticCompatibility();

        assertThat(declaration).isEqualTo(ProviderStaticCompatibility.unknown());
        assertThat(declaration.knowledge()).isEqualTo(ProviderStaticCompatibility.Knowledge.UNKNOWN);
        assertThat(declaration.loweringSupport())
                .isEqualTo(ProviderStaticCompatibility.LoweringSupport.UNKNOWN);
        assertThat(declaration.supportedArtifactRequirements()).isEmpty();
        assertThat(declaration.supportedCodecs()).isEmpty();
        assertThat(declaration.supportedDeviceKinds()).isEmpty();
        assertThat(declaration.supportedRuntimeClasses()).isEmpty();
        assertThat(declaration.supportedSandboxModes()).isEmpty();
        assertThat(declaration.supportedBoundaryContracts()).isEmpty();
    }

    @Test
    void contributionMayOverrideTheDeclaration() {
        // TEST-ONLY double: only declaration identity is asserted here; concrete
        // declared values are built by the provider modules that can see the
        // render-owned component types.
        ProviderStaticCompatibility declared = mock(ProviderStaticCompatibility.class);

        ProviderPluginContribution contribution = contribution(declared);

        assertThat(contribution.providerStaticCompatibility()).isSameAs(declared);
    }

    @Test
    void declarationIsIndependentOfTheCapabilityProfile() {
        ProviderPluginContribution contribution = contribution(null);

        // Capability support stays solely with the capability profile; the static
        // declaration is a separate, non-capability surface.
        assertThat(contribution.providerCapabilityProfile()).isNotNull();
        assertThat(contribution.providerCapabilityProfile().supportDeclarations()).isEmpty();
        assertThat(contribution.providerStaticCompatibility().supportedArtifactRequirements()).isEmpty();
    }

    /** TEST-ONLY contribution double (unrelated members are unused and stay null). */
    private static ProviderPluginContribution contribution(ProviderStaticCompatibility declaration) {
        ProviderBindingPin bindingPin = ProviderCatalogTestFixture.pin("test.provider");
        return new ProviderPluginContribution() {
            @Override public String pluginId() { return "test.provider.plugin"; }
            @Override public String pluginVersion() { return "1.0.0"; }
            @Override public PluginDescriptor pluginDescriptor() { return null; }
            @Override public ProviderDescriptor providerDescriptor() { return null; }
            @Override public ProviderExecutionContract providerExecutionContract() { return null; }
            @Override public ProviderCapabilityProfile providerCapabilityProfile() {
                return ProviderCatalogTestFixture.capabilityProfile();
            }
            @Override public ProviderStaticCompatibility providerStaticCompatibility() {
                return declaration != null
                        ? declaration
                        : ProviderPluginContribution.super.providerStaticCompatibility();
            }
            @Override public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() { return null; }
            @Override public ProviderBindingPin providerBindingPin() { return bindingPin; }
            @Override public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
                    ProviderPluginRuntimeContext context) { return null; }
        };
    }
}
