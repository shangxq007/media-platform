package com.example.platform.federation.graphql.audit;

import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.shared.monitoring.SentryMonitoringService;
import graphql.GraphQLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GraphQLSentryContextContributor {
    private static final Logger log = LoggerFactory.getLogger(GraphQLSentryContextContributor.class);

    private final SentryMonitoringService sentryMonitoringService;

    public GraphQLSentryContextContributor(SentryMonitoringService sentryMonitoringService) {
        this.sentryMonitoringService = sentryMonitoringService;
    }

    public void contribute(GraphQLContext graphQLContext) {
        if (!sentryMonitoringService.isEnabled()) return;

        try {
            GraphQLRequestContext gctx = graphQLContext.get("graphqlContext");
            if (gctx == null) return;

            sentryMonitoringService.setUserContext(
                    gctx.userId() != null ? gctx.userId() : "anonymous",
                    gctx.tenantId() != null ? gctx.tenantId() : "unknown",
                    null
            );
            sentryMonitoringService.setTag("graphql.request_source", gctx.requestSource());
            sentryMonitoringService.setTag("graphql.auth_type", gctx.authType());
            sentryMonitoringService.setTag("trace_id", gctx.traceId());
        } catch (Exception e) {
            log.warn("Failed to contribute Sentry context for GraphQL: {}", e.getMessage());
        }
    }

    public void contributeError(GraphQLContext graphQLContext, Throwable error) {
        if (!sentryMonitoringService.isEnabled()) return;

        try {
            GraphQLRequestContext gctx = graphQLContext.get("graphqlContext");
            if (gctx == null) return;

            sentryMonitoringService.captureException(error, Map.of(
                    "module", "GraphQL",
                    "requestSource", gctx.requestSource() != null ? gctx.requestSource() : "unknown",
                    "userId", gctx.userId() != null ? gctx.userId() : "anonymous",
                    "tenantId", gctx.tenantId() != null ? gctx.tenantId() : "unknown",
                    "traceId", gctx.traceId() != null ? gctx.traceId() : "unknown"
            ));
        } catch (Exception e) {
            log.warn("Failed to capture Sentry error for GraphQL: {}", e.getMessage());
        }
    }
}
