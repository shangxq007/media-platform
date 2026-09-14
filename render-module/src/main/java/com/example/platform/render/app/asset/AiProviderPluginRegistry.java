package com.example.platform.render.app.asset;

import com.example.platform.extension.api.port.ExtensionQueries;
import com.example.platform.extension.api.port.ExtensionQueries.ExtensionInfo;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Read-only view of registered AI provider extension keys.
 *
 * <p>Provider selection and typed {@code ProviderDescriptor} ownership belong
 * to the provider runtime. Render must not synthesize descriptors or select a
 * provider from a capability.</p>
 */
@Component
public class AiProviderPluginRegistry {

    private final ExtensionQueries extensionRegistry;

    public AiProviderPluginRegistry(ExtensionQueries extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    public List<String> listProviders() {
        return extensionRegistry.listExtensions().stream()
                .map(ExtensionInfo::key).toList();
    }

    public int count() {
        return extensionRegistry.listExtensions().size();
    }
}
