package com.example.platform.extension.domain;

/**
 * SPI for dynamic Provider extensions.
 * Allows third-party render providers or AI providers to be registered at runtime.
 */
public interface ProviderExtensionSPI {

    /**
     * Unique identifier for this provider extension.
     */
    String providerKey();

    /**
     * Provider type (RENDER, AI, NOTIFICATION, STORAGE, etc.).
     */
    String providerType();

    /**
     * Semantic version of this provider extension.
     */
    String version();

    /**
     * JSON schema describing the input parameters.
     */
    String inputSchema();

    /**
     * JSON schema describing the output format.
     */
    String outputSchema();

    /**
     * Execute the provider with given input.
     * @param inputJson JSON string containing input parameters
     * @return JSON string containing output
     * @throws ExtensionExecutionException on failure
     */
    String execute(String inputJson) throws ExtensionExecutionException;

    /**
     * Check if this provider is available and healthy.
     */
    boolean isAvailable();

    /**
     * Called when the extension is being unloaded.
     * Use for cleanup: close connections, release resources.
     */
    void onUnload();

    /**
     * Called before rollback to a previous version.
     * @param targetVersion the version being rolled back to
     */
    default void onRollback(String targetVersion) {
        // Default: no-op, subclasses can override
    }
}
