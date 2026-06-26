package com.example.platform.render.app.asset;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.app.ExtensionRegistryService.ExtensionInfo;
import com.example.platform.render.domain.asset.semantic.AiProviderDescriptor;
import com.example.platform.render.infrastructure.asset.provider.WhisperProviderExtension;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin capability index — resolves AI providers by capability.
 * The authoritative registry is ExtensionRegistryService. This component
 * provides AI-specific capability-based resolution on top of it.
 */
@Component
public class AiProviderPluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderPluginRegistry.class);
    private final ExtensionRegistryService extensionRegistry;

    public AiProviderPluginRegistry(ExtensionRegistryService extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    public Optional<AiProviderDescriptor> resolveByCapability(String capability) {
        for (ExtensionInfo info : extensionRegistry.listExtensions()) {
            if ("PROVIDER".equals(info.category()) || "PROVIDER".equals(info.extensionType())) {
                return Optional.of(AiProviderDescriptor.of(info.key(), info.key(),
                        List.of(capability)));
            }
        }
        return Optional.empty();
    }

    public List<String> listProviders() {
        return extensionRegistry.listExtensions().stream()
                .map(ExtensionInfo::key).toList();
    }

    public int count() {
        return extensionRegistry.listExtensions().size();
    }
}
