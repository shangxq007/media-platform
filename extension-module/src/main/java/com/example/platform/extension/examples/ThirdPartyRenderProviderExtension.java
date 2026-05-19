package com.example.platform.extension.examples;

import com.example.platform.extension.domain.*;

public class ThirdPartyRenderProviderExtension implements ProviderExtensionSPIV2 {

    @Override
    public String providerKey() {
        return "provider.third_party.render";
    }

    @Override
    public String providerType() {
        return "RENDER";
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
                    "sourceUri": {"type": "string"},
                    "outputFormat": {"type": "string"},
                    "resolution": {"type": "string"}
                  },
                  "required": ["sourceUri"]
                }
                """;
    }

    @Override
    public String outputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "outputUri": {"type": "string"},
                    "durationMs": {"type": "integer"},
                    "fileSizeBytes": {"type": "integer"}
                  }
                }
                """;
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.SEMI_TRUSTED;
    }

    @Override
    public ExtensionResult execute(ExtensionContext context, String inputJson) throws ExtensionExecutionException {
        try {
            long startTime = System.currentTimeMillis();

            String outputJson = """
                    {"outputUri":"s3://bucket/rendered/video.mp4","format":"mp4","status":"completed"}""";

            long duration = System.currentTimeMillis() - startTime;

            return ExtensionResult.success(outputJson, java.util.Map.of(
                    "durationMs", duration,
                    "provider", providerKey(),
                    "tenantId", context.tenantId() != null ? context.tenantId() : "unknown"
            ));
        } catch (Exception e) {
            return ExtensionResult.failure("PROVIDER_ERROR",
                    "Render failed: " + e.getMessage());
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
                2, 512, 75, 50, 50 * 1024 * 1024, 16 * 1024 * 1024, 60_000
        );
    }
}
