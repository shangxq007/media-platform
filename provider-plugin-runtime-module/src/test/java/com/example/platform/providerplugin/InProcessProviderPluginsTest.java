package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.providerplugin.bmf.BmfProviderExtension;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BMF-PROVIDER-INTEGRATION-001: in-process contributions join the host's own catalog. */
class InProcessProviderPluginsTest {

    @Test
    void catalogWithRegistersTheExtraContributionOnTheSameCatalog() {
        ProviderPluginCatalog hostCatalog = new ProviderPluginCatalog();
        BmfProviderExtension bmf = new BmfProviderExtension();

        assertThat(InProcessProviderPlugins.catalogWith(hostCatalog, List.of(bmf)))
                .isSameAs(hostCatalog);
        assertThat(hostCatalog.contributions()).containsExactly(bmf);
        assertThat(hostCatalog.find(bmf.providerBindingPin())).contains(bmf);
    }

    @Test
    void anEmptyExtraListLeavesTheCatalogUntouched() {
        ProviderPluginCatalog hostCatalog = new ProviderPluginCatalog();

        assertThat(InProcessProviderPlugins.catalogWith(hostCatalog, List.of())).isSameAs(hostCatalog);
        assertThat(hostCatalog.contributions()).isEmpty();
    }

    @Test
    void nullInputsAreRejected() {
        assertThatThrownBy(() -> InProcessProviderPlugins.catalogWith(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> InProcessProviderPlugins.catalogWith(
                new ProviderPluginCatalog(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
