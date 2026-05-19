package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLErrorCodeExtensionBuilderTest {

    @Test
    void buildsExtensionsFromPlatformException() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "ENTITLEMENT-403-001", 403001,
                Map.of("en", "Feature not allowed"),
                "entitlement", 403
        );
        PlatformException ex = new PlatformException(errorCode, "detail message",
                Map.of("featureKey", "gpu_render"), "en");

        Map<String, Object> extensions = GraphQLErrorCodeExtensionBuilder.build(ex, "trace-123");

        assertEquals("ENTITLEMENT-403-001", extensions.get("errorCode"));
        assertEquals("trace-123", extensions.get("traceId"));
        assertNotNull(extensions.get("details"));
    }

    @Test
    void buildsExtensionsFromRawCodes() {
        Map<String, Object> extensions = GraphQLErrorCodeExtensionBuilder.build(
                "COMMON-400-001", "INVALID_REQUEST", "Bad request", "trace-456");

        assertEquals("COMMON-400-001", extensions.get("errorCode"));
        assertEquals("trace-456", extensions.get("traceId"));
        assertEquals("Bad request", extensions.get("message"));
    }

    @Test
    void includesReasonCodeWhenProvided() {
        Map<String, Object> extensions = GraphQLErrorCodeExtensionBuilder.build(
                "COMMON-400-001", "INVALID_REQUEST", "Bad request", "trace-456");

        assertEquals("INVALID_REQUEST", extensions.get("reasonCode"));
    }
}
