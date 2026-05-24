package com.example.platform.extension.examples;

import com.example.platform.extension.domain.*;

public class QualityCheckWorkflowStepExtension implements WorkflowStepExtensionSPI {

    @Override
    public String stepKey() {
        return "workflow.step.quality_check";
    }

    @Override
    public String stepType() {
        return "POST_PROCESS";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "outputUri": {"type": "string"},
                    "expectedResolution": {"type": "string"},
                    "expectedFormat": {"type": "string"},
                    "minDurationSec": {"type": "number"}
                  },
                  "required": ["outputUri"]
                }
                """;
    }

    @Override
    public String outputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "passed": {"type": "boolean"},
                    "checks": {"type": "array"},
                    "score": {"type": "number"}
                  }
                }
                """;
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.SEMI_TRUSTED;
    }

    @Override
    public ExtensionResult execute(ExtensionContext context, String stepInput)
            throws ExtensionExecutionException {
        try {
            boolean passed = true;
            int checksDone = 3;
            double score = 0.95;

            String outputJson = "{\"passed\":" + passed + ",\"checksPerformed\":" + checksDone
                    + ",\"qualityScore\":" + score + "}";

            return ExtensionResult.success(outputJson, java.util.Map.of(
                    "checksPerformed", checksDone,
                    "qualityScore", score,
                    "tenantId", context.tenantId() != null ? context.tenantId() : "unknown",
                    "step", stepKey()
            ));
        } catch (Exception e) {
            return ExtensionResult.failure("QUALITY_CHECK_ERROR",
                    "Quality check failed: " + e.getMessage());
        }
    }

    @Override
    public String executeStep(String stepInput, String workflowContext)
            throws ExtensionExecutionException {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(stepKey())
                .extensionVersion(version())
                .trustLevel(trustLevel())
                .build();
        ExtensionResult result = execute(ctx, stepInput);
        return result.outputJson();
    }

    @Override
    public void onUnload() {
    }

    @Override
    public void onRollback(String targetVersion) {
    }

    @Override
    public ExtensionResourceLimits resourceLimits() {
        return new ExtensionResourceLimits(
                4, 256, 50, 100, 5 * 1024 * 1024, 1024 * 1024, 30_000
        );
    }
}
