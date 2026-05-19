package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

public final class GraphQLExceptionMapper {

    private GraphQLExceptionMapper() {}

    public static GraphQLError map(Throwable ex, DataFetchingEnvironment env, String traceId) {
        if (ex instanceof PlatformException pe) {
            return mapPlatformException(pe, env, traceId);
        }
        if (ex instanceof IllegalArgumentException iae) {
            return mapCommonError(CommonErrorCode.INVALID_REQUEST, iae.getMessage(), env, traceId);
        }
        if (ex instanceof SecurityException se) {
            return mapCommonError(CommonErrorCode.INSUFFICIENT_PERMISSION, se.getMessage(), env, traceId);
        }
        return mapCommonError(CommonErrorCode.INTERNAL_ERROR, "Internal server error", env, traceId);
    }

    private static GraphQLError mapPlatformException(PlatformException ex,
                                                      DataFetchingEnvironment env,
                                                      String traceId) {
        var extensions = GraphQLErrorCodeExtensionBuilder.build(ex, traceId);
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError();
        if (env != null) {
            builder = builder.locations(env.getField() != null
                    ? java.util.List.of(env.getField().getSourceLocation())
                    : java.util.List.of());
        }
        return builder.message(ex.getLocalizedMessage())
                .extensions(extensions)
                .build();
    }

    private static GraphQLError mapCommonError(CommonErrorCode errorCode, String message,
                                                DataFetchingEnvironment env, String traceId) {
        var extensions = GraphQLErrorCodeExtensionBuilder.build(
                errorCode.code(), null, message, traceId);
        GraphqlErrorBuilder<?> builder = GraphqlErrorBuilder.newError();
        if (env != null) {
            builder = builder.locations(env.getField() != null
                    ? java.util.List.of(env.getField().getSourceLocation())
                    : java.util.List.of());
        }
        return builder.message(message != null ? message : errorCode.title())
                .extensions(extensions)
                .build();
    }
}
