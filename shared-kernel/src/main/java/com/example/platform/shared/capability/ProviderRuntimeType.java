package com.example.platform.shared.capability;

/**
 * Runtime type for extension providers.
 */
public enum ProviderRuntimeType {
    BUILTIN,
    HTTP_CONNECTOR,
    BYOK_PROVIDER,
    REVIEWED_PLUGIN,
    SANDBOX_FUNCTION,
    CONTAINER_PLUGIN
}
