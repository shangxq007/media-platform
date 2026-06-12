package com.example.platform.federation.graphql.context;

import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the GraphQL request context from server-side state.
 *
 * <p>User identity is resolved from {@link SecurityContextHolder} (set by auth filters
 * from JWT/OAuth2 claims). The {@code X-User-Id} header is NOT trusted — it was a legacy
 * pattern that allowed client-side user spoofing.
 */
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

        // Tenant ID is resolved exclusively from the server-side TenantContext
        // (set by authentication filters from JWT/OAuth2 claims).
        String tenantId = TenantContext.get();
        String workspaceId = firstHeader(headers, "X-Workspace-Id");

        // User identity is resolved from the authenticated principal, NOT from headers.
        String userId = resolveUserId();

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

        // Roles and permissions come from the authenticated principal, not from headers.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = List.of();
        List<String> permissions = List.of();
        if (auth != null && auth.getAuthorities() != null) {
            roles = auth.getAuthorities().stream()
                    .map(Object::toString)
                    .toList();
        }

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

    /**
     * Resolves the authenticated user ID from SecurityContextHolder.
     * Returns null if no authentication is present (anonymous).
     */
    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        // Spring Security returns "anonymousUser" for anonymous auth
        if ("anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }
}
