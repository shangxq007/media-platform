package com.example.platform.federation.graphql.resolver;

import com.example.platform.extension.app.ExtensionRegistryService;
import com.example.platform.extension.app.ExtensionRegistryService.ExtensionInfo;
import com.example.platform.extension.app.ExtensionResourceLimiter;
import com.example.platform.extension.app.ExtensionRouter;
import com.example.platform.extension.domain.ExtensionResourceLimits;
import com.example.platform.extension.domain.RoutingRule;
import com.example.platform.federation.graphql.context.GraphQLRequestContext;
import com.example.platform.federation.graphql.dto.ResourceLimits;
import com.example.platform.federation.graphql.dto.RouteRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.stream.Collectors;

// @Controller disabled - GraphQL schema conflict
public class ExtensionGraphQLResolver {

    private static final Logger log = LoggerFactory.getLogger(ExtensionGraphQLResolver.class);

    private final ExtensionRegistryService extensionRegistryService;
    private final ExtensionRouter extensionRouter;
    private final ExtensionResourceLimiter extensionResourceLimiter;

    public ExtensionGraphQLResolver(ExtensionRegistryService extensionRegistryService,
                                  ExtensionRouter extensionRouter,
                                  ExtensionResourceLimiter extensionResourceLimiter) {
        this.extensionRegistryService = extensionRegistryService;
        this.extensionRouter = extensionRouter;
        this.extensionResourceLimiter = extensionResourceLimiter;
    }

    @QueryMapping
    public List<com.example.platform.federation.graphql.dto.ExtensionInfo> extensionOverview(GraphQLRequestContext context) {
        List<String> roles = context.roles();
        if (roles == null || (!roles.contains("EXTENSION_ADMIN") && !roles.contains("ADMIN"))) {
            throw new IllegalArgumentException("Access denied: requires EXTENSION_ADMIN or ADMIN role");
        }

        List<ExtensionInfo> extensions = extensionRegistryService.listExtensions();

        return extensions.stream()
                .map(this::mapExtensionInfo)
                .collect(Collectors.toList());
    }

    private com.example.platform.federation.graphql.dto.ExtensionInfo mapExtensionInfo(ExtensionInfo ext) {
        List<RoutingRule> routingRules = extensionRouter.getRules(ext.key());
        List<RouteRule> routeRules = routingRules.stream()
                .map(r -> new RouteRule(r.scene(), r.priority(), r.enabled()))
                .collect(Collectors.toList());

        ExtensionResourceLimits limits = extensionResourceLimiter.getLimits(ext.key());
        ResourceLimits resourceLimits = new ResourceLimits(
                (int) limits.timeoutMs(),
                limits.maxConcurrency(),
                (int) limits.maxOutputBytes()
        );

        return new com.example.platform.federation.graphql.dto.ExtensionInfo(
                ext.key(),
                ext.extensionType(),
                ext.trustLevel(),
                "ACTIVE".equals(ext.status()),
                ext.version(),
                "HEALTHY",
                null,
                routeRules,
                resourceLimits
        );
    }
}
