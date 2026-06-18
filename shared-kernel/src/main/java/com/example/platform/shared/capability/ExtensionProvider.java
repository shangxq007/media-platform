package com.example.platform.shared.capability;

/**
 * Interface for providers that implement extension points.
 *
 * <p>ExtensionProvider is the implementation contract for extensible capabilities.
 * Providers declare which extension points they support and can be invoked
 * through the InvocationContext.</p>
 *
 * <p><strong>Contract only:</strong> This defines the provider interface.
 * No concrete providers are implemented.</p>
 */
public interface ExtensionProvider {

    /**
     * Unique provider identifier.
     */
    String providerId();

    /**
     * Human-readable provider name.
     */
    String providerName();

    /**
     * Extension points supported by this provider.
     */
    java.util.Set<String> supportedExtensionPoints();

    /**
     * Provider capabilities.
     */
    ProviderCapabilities capabilities();

    /**
     * Provider runtime type.
     */
    ProviderRuntimeType runtimeType();

    /**
     * Whether this provider is enabled.
     */
    boolean isEnabled();

    /**
     * Invoke this provider for the given extension point.
     *
     * @param context invocation context (tenant, user, etc.)
     * @param extensionPointKey the extension point to invoke
     * @param request the request payload
     * @return invocation result
     */
    InvocationResult invoke(InvocationContext context, String extensionPointKey, Object request);
}
