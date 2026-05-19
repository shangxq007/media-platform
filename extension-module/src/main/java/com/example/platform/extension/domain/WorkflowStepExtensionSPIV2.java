package com.example.platform.extension.domain;

public interface WorkflowStepExtensionSPIV2 extends WorkflowStepExtensionSPI {

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
}
