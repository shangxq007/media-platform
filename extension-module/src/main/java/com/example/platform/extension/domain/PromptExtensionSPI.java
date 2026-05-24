package com.example.platform.extension.domain;

/**
 * SPI for Prompt template extensions.
 * Allows custom prompt templates, rendering scripts, and post-processing.
 */
public interface PromptExtensionSPI {

    String extensionKey();
    String extensionType();
    String version();

    ExtensionTrustLevel trustLevel();

    ExtensionResult execute(ExtensionContext context, String templateBody, String variables) throws ExtensionExecutionException;

    default String execute(String templateBody, String variables, String contextJson) throws ExtensionExecutionException {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(extensionKey())
                .extensionVersion(version())
                .trustLevel(trustLevel())
                .build();
        ExtensionResult result = execute(ctx, templateBody, variables);
        return result.outputJson();
    }

    default ExtensionResourceLimits resourceLimits() {
        return ExtensionResourceLimits.forTrustLevel(trustLevel());
    }

    String validate(String inputJson);
    void onUnload();
    default void onRollback(String targetVersion) {}
}
