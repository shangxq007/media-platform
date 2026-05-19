package com.example.platform.extension.domain;

public interface ProviderExtensionSPIV2 extends ProviderExtensionSPI {

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
}
