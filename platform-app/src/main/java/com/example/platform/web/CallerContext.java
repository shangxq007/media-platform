package com.example.platform.web;

public record CallerContext(
        String source,
        String userId,
        String tenantId,
        String authType,
        String traceId
) {
    public static final String SOURCE_WEB = "WEB";
    public static final String SOURCE_MCP = "MCP";
    public static final String AUTH_JWT = "JWT_SESSION";
    public static final String AUTH_API_KEY = "API_KEY";

    public boolean isWeb() {
        return SOURCE_WEB.equals(source);
    }

    public boolean isMcp() {
        return SOURCE_MCP.equals(source);
    }
}
