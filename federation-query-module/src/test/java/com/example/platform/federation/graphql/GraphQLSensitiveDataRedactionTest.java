package com.example.platform.federation.graphql;

import com.example.platform.federation.graphql.audit.GraphQLAuditInterceptor;
import com.example.platform.federation.graphql.audit.GraphQLOperationLogger;
import com.example.platform.shared.audit.AuditPort;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphQLSensitiveDataRedactionTest {

    @Mock
    private AuditPort auditPort;

    @SuppressWarnings("unchecked")
    private String invokeRedactVariables(Map<String, Object> variables) throws Exception {
        Method method = GraphQLAuditInterceptor.class.getDeclaredMethod("redactVariables", Map.class);
        method.setAccessible(true);
        GraphQLOperationLogger logger = new GraphQLOperationLogger();
        GraphQLAuditInterceptor interceptor = new GraphQLAuditInterceptor(auditPort, logger);
        return (String) method.invoke(interceptor, variables);
    }

    @Test
    void redactsPasswordVariables() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("username", "testuser");
        variables.put("password", "secret123");
        variables.put("query", "search");

        String redacted = invokeRedactVariables(variables);

        assertTrue(redacted.contains("testuser"), "Should contain non-sensitive values");
        assertTrue(redacted.contains("[REDACTED]"), "Should redact password");
        assertFalse(redacted.contains("secret123"), "Should not contain raw password");
    }

    @Test
    void redactsTokenVariables() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("name", "test");
        variables.put("authToken", "bearer-token-value");

        String redacted = invokeRedactVariables(variables);

        assertTrue(redacted.contains("test"), "Should contain non-sensitive values");
        assertTrue(redacted.contains("[REDACTED]"), "Should redact token");
        assertFalse(redacted.contains("bearer-token-value"), "Should not contain raw token");
    }

    @Test
    void redactsApiKeyVariables() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("apiKey", "sk-12345");
        variables.put("userId", "user-1");

        String redacted = invokeRedactVariables(variables);

        assertTrue(redacted.contains("[REDACTED]"), "Should redact apiKey");
        assertTrue(redacted.contains("user-1"), "Should preserve non-sensitive values");
        assertFalse(redacted.contains("sk-12345"), "Should not contain raw apiKey");
    }

    @Test
    void preservesNonSensitiveVariables() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("projectId", "proj-123");
        variables.put("limit", 20);

        String redacted = invokeRedactVariables(variables);

        assertTrue(redacted.contains("proj-123"), "Should preserve projectId");
        assertTrue(redacted.contains("20"), "Should preserve limit");
        assertFalse(redacted.contains("[REDACTED]"), "Should not redact non-sensitive fields");
    }

    @Test
    void handlesNullVariables() throws Exception {
        String redacted = invokeRedactVariables(null);
        assertEquals("{}", redacted);
    }

    @Test
    void handlesEmptyVariables() throws Exception {
        String redacted = invokeRedactVariables(Map.of());
        assertEquals("{}", redacted);
    }

    @Test
    void sensitiveFieldsAreCaseInsensitive() throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("PASSWORD", "upper-secret");
        variables.put("Secret", "mixed-case-secret");
        variables.put("apiKeyValue", "key-value");

        String redacted = invokeRedactVariables(variables);

        assertTrue(redacted.contains("[REDACTED]"), "Should redact all sensitive fields");
        assertFalse(redacted.contains("upper-secret"), "Should not contain raw PASSWORD");
        assertFalse(redacted.contains("mixed-case-secret"), "Should not contain raw Secret");
        assertFalse(redacted.contains("key-value"), "Should not contain raw apiKeyValue");
    }

    @Test
    void auditInterceptorRecordsRedactedVariables() throws Exception {
        GraphQLOperationLogger operationLogger = new GraphQLOperationLogger();
        GraphQLAuditInterceptor interceptor = new GraphQLAuditInterceptor(auditPort, operationLogger);
        InstrumentationState state = interceptor.createState(null);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("password", "secret123");
        variables.put("username", "testuser");

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query("query($username: String!, $password: String!) { test }")
                .variables(variables)
                .operationName("TestOp")
                .build();

        InstrumentationExecutionParameters params = mock(InstrumentationExecutionParameters.class);
        when(params.getExecutionInput()).thenReturn(executionInput);
        when(params.getVariables()).thenReturn(variables);
        when(params.getGraphQLContext()).thenReturn(GraphQLContext.newContext().build());

        ExecutionResult result = mock(ExecutionResult.class);
        when(result.getErrors()).thenReturn(List.of());

        InstrumentationContext<ExecutionResult> context = interceptor.beginExecution(params, state);
        assertNotNull(context);
        context.onCompleted(result, null);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), payloadCaptor.capture());

        Map<String, Object> capturedPayload = payloadCaptor.getValue();
        String varsRedacted = (String) capturedPayload.get("variablesRedacted");
        assertNotNull(varsRedacted);
        assertTrue(varsRedacted.contains("[REDACTED]"), "Variables should contain redacted value: " + varsRedacted);
        assertFalse(varsRedacted.contains("secret123"), "Variables should not contain raw password: " + varsRedacted);
    }
}
