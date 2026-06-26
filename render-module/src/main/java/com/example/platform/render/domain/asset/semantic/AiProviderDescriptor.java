package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * Descriptor for an AI provider plugin — immutable metadata about the provider's
 * identity, capabilities, supported media types, languages, and models.
 */
public record AiProviderDescriptor(
        String providerId,
        String displayName,
        String version,
        String vendor,
        String description,
        List<String> capabilities,
        List<String> supportedMediaTypes,
        List<String> supportedLanguages,
        List<String> supportedModels,
        String executionBackendType,
        int priority) {

    public static AiProviderDescriptor of(String providerId, String displayName,
                                             List<String> capabilities) {
        return new AiProviderDescriptor(providerId, displayName, "1.0", "community",
                "", capabilities, List.of("VIDEO", "AUDIO"), List.of("en"),
                List.of("base"), "local-process", 50);
    }
}
