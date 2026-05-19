package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLErrorResolverTest {

    @Test
    void resolvesPlatformExceptionToSingleError() {
        GraphQLErrorResolver resolver = new GraphQLErrorResolver();
        ConfigurableErrorCode errorCode = new ConfigurableErrorCode(
                "ENTITLEMENT-403-001", 403001,
                Map.of("en", "Feature not allowed"),
                "entitlement", 403
        );
        PlatformException ex = new PlatformException(errorCode);

        GraphQLError error = resolver.resolveToSingleError(ex, null);

        assertNotNull(error);
        assertEquals("ENTITLEMENT-403-001", error.getExtensions().get("errorCode"));
    }

    @Test
    void resolvesGenericExceptionToInternalError() {
        GraphQLErrorResolver resolver = new GraphQLErrorResolver();
        RuntimeException ex = new RuntimeException("unexpected");

        GraphQLError error = resolver.resolveToSingleError(ex, null);

        assertNotNull(error);
        assertNotNull(error.getExtensions());
    }
}
