package com.example.platform.web;

import com.example.platform.security.JwtProperties;
import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets TenantContext from JWT claims if not already set, then clears after each request.
 *
 * <p>When app.security.enabled=true, JwtAuthFilter/OAuth2RequestContextFilter sets TenantContext.
 * When app.security.enabled=false (dev/preview), this filter extracts tenant from JWT directly.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;

    public TenantContextFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // If TenantContext is not set by auth filters, try to extract from JWT
            if (TenantContext.get() == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    try {
                        String token = authHeader.substring(7);
                        String secretKey = jwtProperties.resolvedSecretKey();
                        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
                        Claims claims = Jwts.parser()
                                .verifyWith(key)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();
                        String tenantId = claims.get("tenantId", String.class);
                        if (tenantId != null && !tenantId.isBlank()) {
                            TenantContext.set(tenantId);
                            MDC.put(TraceKeys.TENANT_ID, tenantId);
                        }
                    } catch (Exception ignored) {
                        // Invalid token, continue without tenant context
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TraceKeys.TENANT_ID);
        }
    }
}
