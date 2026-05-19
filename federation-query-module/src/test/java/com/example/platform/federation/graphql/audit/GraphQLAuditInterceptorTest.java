package com.example.platform.federation.graphql.audit;

import com.example.platform.shared.audit.AuditPort;
import graphql.execution.instrumentation.InstrumentationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphQLAuditInterceptorTest {

    @Mock
    private AuditPort auditPort;

    @Test
    void createsStateSuccessfully() {
        GraphQLOperationLogger operationLogger = new GraphQLOperationLogger();
        GraphQLAuditInterceptor interceptor = new GraphQLAuditInterceptor(auditPort, operationLogger);

        InstrumentationState state = interceptor.createState(null);
        assertNotNull(state);
    }

    @Test
    void createStateReturnsAuditStateInstance() {
        GraphQLOperationLogger operationLogger = new GraphQLOperationLogger();
        GraphQLAuditInterceptor interceptor = new GraphQLAuditInterceptor(auditPort, operationLogger);

        InstrumentationState state = interceptor.createState(null);
        assertInstanceOf(GraphQLAuditInterceptor.AuditState.class, state);
    }
}
