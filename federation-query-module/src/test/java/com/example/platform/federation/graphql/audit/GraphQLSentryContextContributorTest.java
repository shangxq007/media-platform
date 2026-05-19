package com.example.platform.federation.graphql.audit;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.shared.monitoring.SentryMonitoringService;
import graphql.GraphQLContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphQLSentryContextContributorTest {

    @Test
    void contributeDoesNothingWhenSentryDisabled() {
        SentryMonitoringService sentryMonitoringService = mock(SentryMonitoringService.class);
        when(sentryMonitoringService.isEnabled()).thenReturn(false);
        GraphQLSentryContextContributor contributor = new GraphQLSentryContextContributor(sentryMonitoringService);
        GraphQLContext ctx = GraphQLContext.newContext().build();

        assertDoesNotThrow(() -> contributor.contribute(ctx));
        verify(sentryMonitoringService).isEnabled();
        verifyNoMoreInteractions(sentryMonitoringService);
    }

    @Test
    void contributeSetsTagsWhenSentryEnabled() {
        SentryMonitoringService sentryMonitoringService = mock(SentryMonitoringService.class);
        when(sentryMonitoringService.isEnabled()).thenReturn(true);
        GraphQLRequestContext gctx = new GraphQLRequestContext(
                "tenant-1", "ws-1", "user-1",
                List.of("ADMIN"), List.of("read"),
                "GRAPHQL", "JWT_SESSION",
                "trace-123", "req-456",
                "127.0.0.1", "test-agent"
        );
        GraphQLContext ctx = GraphQLContext.newContext()
                .put("graphqlContext", gctx)
                .build();

        GraphQLSentryContextContributor contributor = new GraphQLSentryContextContributor(sentryMonitoringService);
        contributor.contribute(ctx);

        verify(sentryMonitoringService).setUserContext("user-1", "tenant-1", null);
        verify(sentryMonitoringService).setTag("graphql.request_source", "GRAPHQL");
        verify(sentryMonitoringService).setTag("graphql.auth_type", "JWT_SESSION");
    }

    @Test
    void contributeErrorCapturesExceptionWhenEnabled() {
        SentryMonitoringService sentryMonitoringService = mock(SentryMonitoringService.class);
        when(sentryMonitoringService.isEnabled()).thenReturn(true);
        GraphQLRequestContext gctx = new GraphQLRequestContext(
                "tenant-1", null, "user-1",
                List.of(), List.of(),
                "GRAPHQL", null,
                "trace-123", null,
                null, null
        );
        GraphQLContext ctx = GraphQLContext.newContext()
                .put("graphqlContext", gctx)
                .build();

        GraphQLSentryContextContributor contributor = new GraphQLSentryContextContributor(sentryMonitoringService);
        RuntimeException error = new RuntimeException("test error");
        contributor.contributeError(ctx, error);

        verify(sentryMonitoringService).captureException(any(RuntimeException.class), any(Map.class));
    }
}
