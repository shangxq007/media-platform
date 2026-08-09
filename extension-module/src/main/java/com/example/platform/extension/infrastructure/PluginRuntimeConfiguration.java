package com.example.platform.extension.infrastructure;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.runtime.PluginRuntime;
import com.example.platform.extension.runtime.internal.DefaultPluginRuntime;
import com.example.platform.extension.runtime.internal.ProviderExtensionSpiRuntimeAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UWEV1-FV1-CRR2: canonical PluginRuntime production composition (DEFECT-2 repair).
 *
 * <p>Explicit composition at the extension-module composition root: the public
 * {@link PluginRuntime} contract is exposed as exactly one bean; the internal
 * implementation ({@link DefaultPluginRuntime}) stays in
 * {@code extension.runtime.internal} — never annotated as a component, never
 * moved out of internal, never a second executor (PLUGIN_RUNTIME_IS_SINGULAR).</p>
 */
@Configuration
public class PluginRuntimeConfiguration {

    @Bean
    public PluginRuntime pluginRuntime(ExtensionRegistryService registry) {
        return new DefaultPluginRuntime(new ProviderExtensionSpiRuntimeAdapter(registry));
    }
}
