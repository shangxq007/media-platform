package com.example.platform.providerplugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

class ProviderPluginHostLifecycleTest {

    @Test
    void close_stops_and_unloads_pf4j_then_removes_all_typed_contributions() {
        PluginManager manager = mock(PluginManager.class);
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginId()).thenReturn("plugin.ffmpeg");
        when(manager.getStartedPlugins()).thenReturn(List.of(wrapper));
        when(manager.getPlugins()).thenReturn(List.of(wrapper));
        ProviderPluginHost host = new ProviderPluginHost(manager, mock(PluginRegistryImpl.class));
        host.catalog().register(contribution("plugin.ffmpeg", "1.0.0"));

        host.close();

        var lifecycle = inOrder(manager);
        lifecycle.verify(manager).stopPlugin("plugin.ffmpeg");
        lifecycle.verify(manager).unloadPlugin("plugin.ffmpeg");
        assertThat(host.catalog().contributions()).isEmpty();
    }

    @Test
    void load_failure_stops_and_unloads_pf4j_and_clears_partial_typed_catalog() {
        PluginManager manager = mock(PluginManager.class);
        doThrow(new IllegalStateException("load failed")).when(manager).loadPlugins();
        ProviderPluginHost host = new ProviderPluginHost(manager, mock(PluginRegistryImpl.class));
        host.catalog().register(contribution("plugin.partial", "1.0.0"));

        assertThatThrownBy(host::loadAndStart)
                .isInstanceOf(ProviderPluginLoadException.class)
                .hasMessageContaining("PF4J_PROVIDER_PLUGIN_LOAD_FAILED");

        var lifecycle = inOrder(manager);
        lifecycle.verify(manager).loadPlugins();
        lifecycle.verify(manager).stopPlugins();
        lifecycle.verify(manager).unloadPlugins();
        assertThat(host.catalog().contributions()).isEmpty();
    }

    private static ProviderPluginContribution contribution(String pluginId, String version) {
        ProviderBindingPin pin = mock(ProviderBindingPin.class);
        return new ProviderPluginContribution() {
            @Override public String pluginId() { return pluginId; }
            @Override public String pluginVersion() { return version; }
            @Override public PluginDescriptor pluginDescriptor() { return null; }
            @Override public ProviderDescriptor providerDescriptor() { return null; }
            @Override public ProviderExecutionContract providerExecutionContract() { return null; }
            @Override public ProviderCapabilityProfile providerCapabilityProfile() { return null; }
            @Override public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() { return null; }
            @Override public ProviderBindingPin providerBindingPin() { return pin; }
            @Override public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
                    ProviderPluginRuntimeContext context) { return null; }
        };
    }
}
