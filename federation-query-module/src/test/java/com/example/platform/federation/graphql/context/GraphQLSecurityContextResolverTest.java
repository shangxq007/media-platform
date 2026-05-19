package com.example.platform.federation.graphql.context;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLSecurityContextResolverTest {

    @Test
    void checkAuthorizationReturnsTrueForMatchingRole() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertTrue(resolver.checkAuthorization(auth, "ROLE_ADMIN"));
    }

    @Test
    void checkAuthorizationReturnsFalseForMissingRole() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertFalse(resolver.checkAuthorization(auth, "ROLE_ADMIN"));
    }

    @Test
    void checkAuthorizationReturnsTrueForNullRequiredRole() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertTrue(resolver.checkAuthorization(auth, null));
    }

    @Test
    void checkAuthorizationReturnsFalseForNullAuth() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();

        assertFalse(resolver.checkAuthorization(null, "ROLE_ADMIN"));
    }

    @Test
    void requireRoleSucceedsWithMatchingRole() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertDoesNotThrow(() -> resolver.requireRole(auth, "ROLE_ADMIN").block());
    }

    @Test
    void requireRoleThrowsForMissingRole() {
        GraphQLSecurityContextResolver resolver = new GraphQLSecurityContextResolver();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThrows(AccessDeniedException.class,
                () -> resolver.requireRole(auth, "ROLE_ADMIN").block());
    }
}
