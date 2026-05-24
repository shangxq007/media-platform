package com.example.platform.extension.domain;

/**
 * SPI for dynamic Provider extensions.
 * Allows third-party render providers or AI providers to be registered at runtime.
 */
public interface ProviderExtensionSPI {

    String providerKey();
    String providerType();
    String version();
    String inputSchema();
    String outputSchema();

    ExtensionTrustLevel trustLevel();

    ExtensionResult execute(ExtensionContext context, String inputJson) throws ExtensionExecutionException;

    default String execute(String inputJson) throws ExtensionExecutionException {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(providerKey())
                .extensionVersion(version())
                .trustLevel(trustLevel())
                .build();
        ExtensionResult result = execute(ctx, inputJson);
        return result.outputJson();
    }

    default ExtensionResourceLimits resourceLimits() {
        return ExtensionResourceLimits.forTrustLevel(trustLevel());
    }

    boolean isAvailable();
    void onUnload();
    default void onRollback(String targetVersion) {}
}
