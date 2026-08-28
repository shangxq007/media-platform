package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import org.junit.jupiter.api.Test;

class ProviderPluginCatalogTest {

    @Test
    void duplicate_plugin_identity_and_binding_pin_fail_closed() {
        ProviderPluginCatalog catalog = new ProviderPluginCatalog();
        ProviderBindingPin pin = mock(ProviderBindingPin.class);
        ProviderPluginContribution first = contribution("plugin.ffmpeg", "1.0.0", pin);
        ProviderPluginContribution duplicateIdentity = contribution("plugin.ffmpeg", "1.0.0", pin);

        catalog.register(first);

        assertThatThrownBy(() -> catalog.register(duplicateIdentity))
                .isInstanceOf(ProviderPluginLoadException.class)
                .hasMessageContaining("DUPLICATE_PLUGIN_ID_VERSION");
        assertThat(catalog.contributions()).containsExactly(first);
    }

    @Test
    void distinct_plugin_identity_cannot_claim_an_existing_provider_binding_pin() {
        ProviderPluginCatalog catalog = new ProviderPluginCatalog();
        ProviderBindingPin pin = mock(ProviderBindingPin.class);
        ProviderPluginContribution first = contribution("plugin.ffmpeg", "1.0.0", pin);
        ProviderPluginContribution duplicatePin = contribution("plugin.other", "2.0.0", pin);

        catalog.register(first);

        assertThatThrownBy(() -> catalog.register(duplicatePin))
                .isInstanceOf(ProviderPluginLoadException.class)
                .hasMessageContaining("DUPLICATE_PROVIDER_BINDING_PIN");
        assertThat(catalog.contributions()).containsExactly(first);
    }

    private static ProviderPluginContribution contribution(
            String pluginId, String version, ProviderBindingPin bindingPin) {
        return new ProviderPluginContribution() {
            @Override public String pluginId() { return pluginId; }
            @Override public String pluginVersion() { return version; }
            @Override public PluginDescriptor pluginDescriptor() { return null; }
            @Override public ProviderDescriptor providerDescriptor() { return null; }
            @Override public ProviderExecutionContract providerExecutionContract() { return null; }
            @Override public ProviderCapabilityProfile providerCapabilityProfile() { return null; }
            @Override public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() { return null; }
            @Override public ProviderBindingPin providerBindingPin() { return bindingPin; }
            @Override public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
                    ProviderPluginRuntimeContext context) { return null; }
        };
    }
}
