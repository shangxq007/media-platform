package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphQLErrorCodeExtensionBuilder {

    private GraphQLErrorCodeExtensionBuilder() {}

    public static Map<String, Object> build(PlatformException ex, String traceId) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        ErrorCode errorCode = ex.getErrorCode();
        extensions.put("errorCode", errorCode.code());
        if (errorCode instanceof com.example.platform.shared.web.ConfigurableErrorCode ce) {
            extensions.put("reasonCode", ce.code());
        }
        extensions.put("traceId", traceId);
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            extensions.put("details", ex.getDetails());
        }
        return extensions;
    }

    public static Map<String, Object> build(String errorCode, String reasonCode,
                                             String message, String traceId) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("errorCode", errorCode);
        if (reasonCode != null) {
            extensions.put("reasonCode", reasonCode);
        }
        extensions.put("message", message);
        extensions.put("traceId", traceId);
        return extensions;
    }
}
