package com.example.platform.extension.domain;

public interface PromptExtensionSPIV2 extends PromptExtensionSPI {

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
}
