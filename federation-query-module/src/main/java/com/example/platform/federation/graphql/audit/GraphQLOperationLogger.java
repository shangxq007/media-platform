package com.example.platform.federation.graphql.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GraphQLOperationLogger {
    private static final Logger log = LoggerFactory.getLogger(GraphQLOperationLogger.class);

    public void logOperation(String operationName, String queryHash, String variablesRedacted,
                              String tenantId, String userId, String traceId,
                              long durationMs, int complexity, int depth,
                              String resultStatus, String errorCode) {
        log.info("GraphQL operation={} queryHash={} vars={} tenant={} user={} traceId={} durationMs={} complexity={} depth={} status={} errorCode={}",
                operationName, queryHash, variablesRedacted, tenantId, userId, traceId,
                durationMs, complexity, depth, resultStatus, errorCode);
    }

    public void logError(String operationName, String queryHash, String tenantId,
                          String userId, String traceId, String errorCode, String errorMessage) {
        log.warn("GraphQL error operation={} queryHash={} tenant={} user={} traceId={} errorCode={} message={}",
                operationName, queryHash, tenantId, userId, traceId, errorCode, errorMessage);
    }
}
