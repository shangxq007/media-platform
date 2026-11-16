package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

/** In-process contributions join the host's own catalog; the host knows no concrete provider. */
class InProcessProviderPluginsTest {

    private static ProviderPluginContribution contribution(String provider) {
        ProviderPluginContribution contribution = mock(ProviderPluginContribution.class);
        when(contribution.pluginId()).thenReturn(provider);
        when(contribution.pluginVersion()).thenReturn("1.0.0");
        when(contribution.providerBindingPin()).thenReturn(ProviderCatalogTestFixture.pin(provider));
        return contribution;
    }

    @Test
    void catalogWithRegistersTheExtraContributionOnTheSameCatalog() {
        ProviderPluginCatalog hostCatalog = new ProviderPluginCatalog();
        ProviderPluginContribution extra = contribution("test.provider");

        assertThat(InProcessProviderPlugins.catalogWith(hostCatalog, List.of(extra)))
                .isSameAs(hostCatalog);
        assertThat(hostCatalog.contributions()).containsExactly(extra);
        assertThat(hostCatalog.find(extra.providerBindingPin())).contains(extra);
    }

    @Test
    void anEmptyExtraListLeavesTheCatalogUntouched() {
        ProviderPluginCatalog hostCatalog = new ProviderPluginCatalog();

        assertThat(InProcessProviderPlugins.catalogWith(hostCatalog, List.of())).isSameAs(hostCatalog);
        assertThat(hostCatalog.contributions()).isEmpty();
    }

    @Test
    void duplicateContributionIdentityIsRejected() {
        ProviderPluginCatalog hostCatalog = new ProviderPluginCatalog();
        ProviderPluginContribution first = contribution("test.provider");
        ProviderPluginContribution second = contribution("test.provider");

        InProcessProviderPlugins.catalogWith(hostCatalog, List.of(first));
        assertThatThrownBy(() -> InProcessProviderPlugins.catalogWith(hostCatalog, List.of(second)))
                .hasMessageContaining("DUPLICATE_PLUGIN_ID_VERSION");
    }
}
