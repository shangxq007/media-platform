package com.example.platform.federation.graphql.context;

import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GraphQLContextFactory implements WebGraphQlInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GraphQLContextFactory.class);

    private static String firstHeader(HttpHeaders headers, String key) {
        List<String> values = headers.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        HttpHeaders headers = request.getHeaders();

        String tenantId = firstHeader(headers, "X-Tenant-Id") != null
                ? firstHeader(headers, "X-Tenant-Id")
                : TenantContext.get();
        String workspaceId = firstHeader(headers, "X-Workspace-Id");
        String userId = firstHeader(headers, "X-User-Id");
        String requestSource = firstHeader(headers, "X-Request-Source") != null
                ? firstHeader(headers, "X-Request-Source")
                : "GRAPHQL";
        String authType = firstHeader(headers, "X-Auth-Type");
        String traceId = firstHeader(headers, "X-Trace-Id") != null
                ? firstHeader(headers, "X-Trace-Id")
                : UUID.randomUUID().toString().replace("-", "");
        String requestId = firstHeader(headers, "X-Request-Id") != null
                ? firstHeader(headers, "X-Request-Id")
                : UUID.randomUUID().toString().replace("-", "");
        String forwardedFor = firstHeader(headers, "X-Forwarded-For");
        String ip = forwardedFor != null
                ? forwardedFor.split(",")[0].trim()
                : firstHeader(headers, "X-Real-Ip");
        String userAgent = firstHeader(headers, "User-Agent");

        String rolesHeader = firstHeader(headers, "X-User-Roles");
        List<String> roles = rolesHeader != null
                ? List.of(rolesHeader.split(","))
                : List.of();
        String permsHeader = firstHeader(headers, "X-User-Permissions");
        List<String> permissions = permsHeader != null
                ? List.of(permsHeader.split(","))
                : List.of();

        GraphQLRequestContext context = new GraphQLRequestContext(
                tenantId, workspaceId, userId, roles, permissions,
                requestSource, authType, traceId, requestId, ip, userAgent
        );

        request.configureExecutionInput((executionInput, builder) -> {
            Map<String, Object> gqlCtx = new HashMap<>();
            gqlCtx.put("graphqlContext", context);
            builder.graphQLContext(gqlCtx);
            return builder.build();
        });

        return chain.next(request);
    }
}
