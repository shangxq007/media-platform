package com.example.platform.federation.graphql.audit;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.shared.audit.AuditPort;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GraphQLAuditInterceptor extends SimplePerformantInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(GraphQLAuditInterceptor.class);

    private final AuditPort auditPort;
    private final GraphQLOperationLogger operationLogger;

    public GraphQLAuditInterceptor(AuditPort auditPort, GraphQLOperationLogger operationLogger) {
        this.auditPort = auditPort;
        this.operationLogger = operationLogger;
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new AuditState();
    }

    public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput,
                                                    InstrumentationExecutionParameters parameters,
                                                    InstrumentationState state) {
        AuditState auditState = (AuditState) state;
        auditState.startTime = Instant.now();
        auditState.operationName = executionInput.getOperationName();
        auditState.query = executionInput.getQuery();
        return executionInput;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters, InstrumentationState state) {
        AuditState auditState = (AuditState) state;
        auditState.startTime = Instant.now();
        if (parameters.getExecutionInput() != null) {
            auditState.operationName = parameters.getExecutionInput().getOperationName();
            auditState.query = parameters.getExecutionInput().getQuery();
        }

        return new InstrumentationContext<>() {
            @Override
            public void onDispatched() {}

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
                long durationMs = Instant.now().toEpochMilli() - auditState.startTime.toEpochMilli();
                String queryHash = hashQuery(auditState.query);
                String variablesRedacted = redactVariables(parameters.getVariables());
                String resultStatus = t != null ? "ERROR" : (result.getErrors().isEmpty() ? "SUCCESS" : "PARTIAL");
                String errorCode = extractErrorCode(result, t);

                GraphQLRequestContext gctx = parameters.getGraphQLContext().get("graphqlContext");
                String tenantId = gctx != null ? gctx.tenantId() : null;
                String userId = gctx != null ? gctx.userId() : null;
                String traceId = gctx != null ? gctx.traceId() : null;

                operationLogger.logOperation(
                        auditState.operationName, queryHash, variablesRedacted,
                        tenantId, userId, traceId,
                        durationMs, 0, 0, resultStatus, errorCode
                );

                if (auditPort != null) {
                    try {
                        auditPort.record("GRAPHQL", "EXECUTE", "GRAPHQL_OPERATION",
                                "graphql_operation", auditState.operationName,
                                Map.of(
                                        "queryHash", queryHash,
                                        "variablesRedacted", variablesRedacted,
                                        "tenantId", tenantId != null ? tenantId : "unknown",
                                        "userId", userId != null ? userId : "unknown",
                                        "traceId", traceId != null ? traceId : "unknown",
                                        "durationMs", durationMs,
                                        "resultStatus", resultStatus,
                                        "errorCode", errorCode != null ? errorCode : ""
                                ));
                    } catch (Exception e) {
                        log.warn("Failed to record GraphQL audit: {}", e.getMessage());
                    }
                }
            }
        };
    }

    private String hashQuery(String query) {
        if (query == null) return "none";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(query.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().withLowerCase().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return "hash-error";
        }
    }

    private String redactVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return "{}";
        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key.toLowerCase().contains("password") || key.toLowerCase().contains("secret")
                    || key.toLowerCase().contains("token") || key.toLowerCase().contains("apikey")) {
                redacted.put(key, "[REDACTED]");
            } else {
                redacted.put(key, entry.getValue());
            }
        }
        return redacted.toString();
    }

    private String extractErrorCode(ExecutionResult result, Throwable t) {
        if (t != null) {
            if (t instanceof com.example.platform.shared.web.PlatformException pe) {
                return pe.getErrorCode().code();
            }
            return "INTERNAL_ERROR";
        }
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            Map<String, Object> extensions = result.getErrors().get(0).getExtensions();
            if (extensions != null && extensions.get("errorCode") != null) {
                return extensions.get("errorCode").toString();
            }
            return "GRAPHQL_ERROR";
        }
        return null;
    }

    static class AuditState implements InstrumentationState {
        Instant startTime;
        String operationName;
        String query;
    }
}
