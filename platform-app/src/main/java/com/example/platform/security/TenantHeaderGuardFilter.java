package com.example.platform.security;

import com.example.platform.shared.web.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects {@code X-Tenant-ID} that disagrees with JWT {@code tenantId} when
 * {@code app.security.oauth2.trust-jwt-tenant-only=true}.
 */
public class TenantHeaderGuardFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final OAuth2SecurityProperties oauth2Properties;

    public TenantHeaderGuardFilter(OAuth2SecurityProperties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !oauth2Properties.enabled() || !oauth2Properties.trustJwtTenantOnly();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Object jwtTenant = request.getAttribute("jwt.tenantId");
        if (jwtTenant == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String headerTenant = request.getHeader(TENANT_HEADER);
        if (headerTenant != null && !headerTenant.isBlank()
                && !headerTenant.trim().equals(jwtTenant.toString())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                            + "\"detail\":\"X-Tenant-ID does not match authenticated tenant\","
                            + "\"code\":\"" + CommonErrorCode.INSUFFICIENT_PERMISSION.code() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
