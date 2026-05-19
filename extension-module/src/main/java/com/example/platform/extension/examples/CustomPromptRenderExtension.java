package com.example.platform.extension.examples;

import com.example.platform.extension.domain.*;

public class CustomPromptRenderExtension implements PromptExtensionSPIV2 {

    @Override
    public String extensionKey() {
        return "prompt.extension.custom_render";
    }

    @Override
    public String extensionType() {
        return "RENDER_SCRIPT";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public ExtensionTrustLevel trustLevel() {
        return ExtensionTrustLevel.SEMI_TRUSTED;
    }

    @Override
    public ExtensionResult execute(ExtensionContext context, String templateBody, String variables)
            throws ExtensionExecutionException {
        try {
            String rendered = templateBody;
            if (variables != null && variables.startsWith("{")) {
                String varContent = variables;
                if (varContent.startsWith("{") && varContent.endsWith("}")) {
                    varContent = varContent.substring(1, varContent.length() - 1);
                }
                String[] pairs = varContent.split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("\"", "");
                        String value = kv[1].trim().replace("\"", "");
                        rendered = rendered.replace("{{" + key + "}}", value);
                    }
                }
            }

            long variableCount = rendered.chars().filter(c -> c == '{').count();
            boolean hasUnresolved = variableCount > 0;

            String outputJson = "{\"rendered\":\"" + rendered.replace("\"", "\\\"")
                    + "\",\"unresolvedVariables\":" + hasUnresolved + "}";

            return ExtensionResult.success(outputJson, java.util.Map.of(
                    "inputLength", templateBody.length(),
                    "outputLength", rendered.length(),
                    "hasUnresolved", hasUnresolved,
                    "tenantId", context.tenantId() != null ? context.tenantId() : "unknown"
            ));
        } catch (Exception e) {
            return ExtensionResult.failure("RENDER_ERROR",
                    "Prompt rendering failed: " + e.getMessage());
        }
    }

    @Override
    public String execute(String templateBody, String variables, String contextJson)
            throws ExtensionExecutionException {
        ExtensionContext ctx = ExtensionContext.builder()
                .extensionKey(extensionKey())
                .extensionVersion(version())
                .trustLevel(trustLevel())
                .build();
        ExtensionResult result = execute(ctx, templateBody, variables);
        return result.outputJson();
    }

    @Override
    public String validate(String inputJson) {
        return "{\"valid\":true,\"errors\":[]}";
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
                8, 128, 25, 200, 1024 * 1024, 512 * 1024, 15_000
        );
    }
}
