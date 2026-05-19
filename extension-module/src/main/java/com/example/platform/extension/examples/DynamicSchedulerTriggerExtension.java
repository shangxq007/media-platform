package com.example.platform.extension.examples;

import com.example.platform.extension.domain.*;

import java.util.Map;

public class DynamicSchedulerTriggerExtension implements ProviderExtensionSPIV2 {

    @Override
    public String providerKey() {
        return "scheduler.job.dynamic_cleanup";
    }

    @Override
    public String providerType() {
        return "SCHEDULER_JOB";
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
                    "jobType": {"type": "string"},
                    "targetTable": {"type": "string"},
                    "retentionDays": {"type": "integer"},
                    "batchSize": {"type": "integer"}
                  },
                  "required": ["jobType"]
                }
                """;
    }

    @Override
    public String outputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "status": {"type": "string"},
                    "recordsProcessed": {"type": "integer"},
                    "durationMs": {"type": "integer"}
                  }
                }
                """;
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.FULLY_TRUSTED;
    }

    @Override
    public ExtensionResult execute(ExtensionContext context, String inputJson)
            throws ExtensionExecutionException {
        try {
            long startTime = System.currentTimeMillis();
            int recordsProcessed = 0;
            String status = "completed";

            if (inputJson != null && inputJson.contains("dryRun")) {
                status = "dry_run";
                recordsProcessed = 0;
            } else {
                recordsProcessed = 42;
            }

            long duration = System.currentTimeMillis() - startTime;

            String outputJson = "{\"status\":\"" + status + "\",\"recordsProcessed\":" + recordsProcessed + "}";

            return ExtensionResult.success(outputJson, Map.of(
                    "durationMs", duration,
                    "recordsProcessed", recordsProcessed,
                    "jobType", "dynamic_cleanup",
                    "tenantId", context.tenantId() != null ? context.tenantId() : "unknown"
            ));
        } catch (Exception e) {
            return ExtensionResult.failure("SCHEDULER_JOB_ERROR",
                    "Scheduler job failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
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
                1, 128, 25, 5, 1024 * 1024, 512 * 1024, 300_000
        );
    }
}
