package com.example.platform.web;

import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Clears {@link TenantContext} after each request to prevent thread-local leakage.
 *
 * <p>TenantContext is now established exclusively by authentication filters
 * ({@link com.example.platform.security.JwtAuthFilter},
 * {@link com.example.platform.security.OAuth2RequestContextFilter}) from verified JWT claims.
 * The {@code X-Tenant-ID} header is no longer trusted as a tenant source.
 *
 * <p>The {@code X-Tenant-ID} header fallback was previously used for local dev
 * with {@code app.security.enabled=false}. This has been removed as a security hardening
 * measure. If a request reaches this filter without TenantContext set, it means no valid
 * authentication was provided, and downstream code should reject the request.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TraceKeys.TENANT_ID);
        }
    }
}
