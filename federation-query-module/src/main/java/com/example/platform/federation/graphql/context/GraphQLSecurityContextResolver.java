package com.example.platform.federation.graphql.context;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class GraphQLSecurityContextResolver {

    public Mono<GraphQLRequestContext> resolve() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(GraphQLSecurityContextResolver::fromAuthentication)
                .defaultIfEmpty(new GraphQLRequestContext(
                        null, null, null, List.of(), List.of(),
                        "GRAPHQL", null, null, null, null, null
                ));
    }

    public boolean checkAuthorization(Authentication auth, String requiredRole) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (requiredRole == null || requiredRole.isBlank()) {
            return true;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredRole));
    }

    public Mono<Void> requireRole(Authentication auth, String requiredRole) {
        if (!checkAuthorization(auth, requiredRole)) {
            return Mono.error(new AccessDeniedException(
                    "Access denied: requires role " + requiredRole));
        }
        return Mono.empty();
    }

    private static GraphQLRequestContext fromAuthentication(Authentication auth) {
        String userId = auth != null ? auth.getName() : null;
        List<String> roles = auth != null && auth.getAuthorities() != null
                ? auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .toList()
                : List.of();
        return new GraphQLRequestContext(
                null, null, userId, roles, List.of(),
                "GRAPHQL",
                auth != null ? auth.getClass().getSimpleName() : null,
                null, null, null, null
        );
    }
}
