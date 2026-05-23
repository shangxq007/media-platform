package com.example.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs JIT provisioning after the OIDC JWT is validated and request attributes are set. */
public class OidcUserProvisioningFilter extends OncePerRequestFilter {

    private final OidcIdentityProvisioningService provisioningService;

    public OidcUserProvisioningFilter(OidcIdentityProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            try {
                provisioningService.provisionFromJwt(jwt);
            } catch (Exception ex) {
                // Do not block API calls on provisioning failures (e.g. H2 without RBAC tables in partial tests)
                org.slf4j.LoggerFactory.getLogger(OidcUserProvisioningFilter.class)
                        .warn("OIDC JIT provisioning failed for sub={}: {}", jwt.getSubject(), ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
