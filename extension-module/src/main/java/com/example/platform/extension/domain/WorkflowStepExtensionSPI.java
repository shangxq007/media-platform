package com.example.platform.extension.domain;

/**
 * SPI for Workflow step extensions.
 * Allows custom workflow steps to be injected into render pipelines.
 */
public interface WorkflowStepExtensionSPI {

    String stepKey();
    String stepType();
    String version();
    String inputSchema();
    String outputSchema();

    ExtensionTrustLevel trustLevel();

    ExtensionResult execute(ExtensionContext context, String stepInput) throws ExtensionExecutionException;

    default String executeStep(String stepInput, String workflowContext) throws ExtensionExecutionException {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(stepKey())
                .extensionVersion(version())
                .trustLevel(trustLevel())
                .build();
        ExtensionResult result = execute(ctx, stepInput);
        return result.outputJson();
    }

    default ExtensionResourceLimits resourceLimits() {
        return ExtensionResourceLimits.forTrustLevel(trustLevel());
    }

    void onUnload();
    default void onRollback(String targetVersion) {}
}
