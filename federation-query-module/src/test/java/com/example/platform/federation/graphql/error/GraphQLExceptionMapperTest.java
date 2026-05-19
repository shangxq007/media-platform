package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLExceptionMapperTest {

    @Test
    void mapsPlatformExceptionToGraphQLError() {
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "ENTITLEMENT-403-001", 403001,
                Map.of("en", "Feature not allowed"),
                "entitlement", 403
        );
        PlatformException ex = new PlatformException(errorCode);
        DataFetchingEnvironment env = null;

        GraphQLError error = GraphQLExceptionMapper.map(ex, env, "trace-123");

        assertNotNull(error);
        assertNotNull(error.getExtensions());
        assertEquals("ENTITLEMENT-403-001", error.getExtensions().get("errorCode"));
    }

    @Test
    void mapsIllegalArgumentExceptionToBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        DataFetchingEnvironment env = null;

        GraphQLError error = GraphQLExceptionMapper.map(ex, env, "trace-456");

        assertNotNull(error);
        assertEquals(CommonErrorCode.INVALID_REQUEST.code(), error.getExtensions().get("errorCode"));
    }

    @Test
    void mapsSecurityExceptionToForbidden() {
        SecurityException ex = new SecurityException("access denied");
        DataFetchingEnvironment env = null;

        GraphQLError error = GraphQLExceptionMapper.map(ex, env, "trace-789");

        assertNotNull(error);
        assertEquals(CommonErrorCode.INSUFFICIENT_PERMISSION.code(), error.getExtensions().get("errorCode"));
    }

    @Test
    void mapsUnknownExceptionToInternalError() {
        RuntimeException ex = new RuntimeException("unexpected");
        DataFetchingEnvironment env = null;

        GraphQLError error = GraphQLExceptionMapper.map(ex, env, "trace-000");

        assertNotNull(error);
        assertEquals(CommonErrorCode.INTERNAL_ERROR.code(), error.getExtensions().get("errorCode"));
    }
}
