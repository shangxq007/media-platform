package com.example.platform.federation.graphql.error;

import com.example.platform.shared.web.PlatformException;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

@Component
public class GraphQLErrorResolver extends DataFetcherExceptionResolverAdapter {
    private static final Logger log = LoggerFactory.getLogger(GraphQLErrorResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String traceId = extractTraceId(env);
        String executionId = env != null ? String.valueOf(env.getExecutionId()) : "unknown";
        log.error("GraphQL error in {}: {}", executionId, ex.getMessage(), ex);
        return GraphQLExceptionMapper.map(ex, env, traceId);
    }

    private String extractTraceId(DataFetchingEnvironment env) {
        if (env == null) return null;
        try {
            Object ctx = env.getGraphQlContext().get("graphqlContext");
            if (ctx instanceof com.example.platform.federation.graphql.context.GraphQLRequestContext gctx) {
                return gctx.traceId();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
