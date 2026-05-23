package com.example.platform.security;

import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Configuration
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "false", matchIfMissing = true)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;

    public JwtAuthFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    @Order(3)
    FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(this);
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(3);
        registration.setEnabled(true);
        return registration;
    }

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> WEB_API_PREFIXES = Set.of(
            "/api/v1/render/jobs",
            "/api/v1/prompts",
            "/api/v1/tenants",
            "/api/v1/artifacts",
            "/api/v1/web/"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : WEB_API_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Authentication Required\",\"status\":401,"
                            + "\"detail\":\"Missing or malformed JWT token\",\"code\":\""
                            + CommonErrorCode.AUTHENTICATION_REQUIRED.code() + "\"}");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String tenantId = claims.get("tenantId", String.class);
            List<String> roles = claims.get("roles", List.class);

            request.setAttribute("jwt.subject", subject);
            request.setAttribute("jwt.tenantId", tenantId);
            request.setAttribute("jwt.roles", roles);
            request.setAttribute("request.source", "WEB");

            if (tenantId != null) {
                TenantContext.set(tenantId);
                MDC.put(TraceKeys.TENANT_ID, tenantId);
            }
            MDC.put("principal", subject != null ? subject : "anonymous");

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
                MDC.remove(TraceKeys.TENANT_ID);
                MDC.remove("principal");
            }
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Authentication Required\",\"status\":401,"
                            + "\"detail\":\"Invalid or expired JWT token\",\"code\":\""
                            + CommonErrorCode.AUTHENTICATION_REQUIRED.code() + "\"}");
        }
    }
}
