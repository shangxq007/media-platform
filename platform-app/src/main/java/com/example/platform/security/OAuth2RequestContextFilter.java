package com.example.platform.security;

import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Maps validated OIDC JWTs into {@code jwt.*} request attributes and {@link TenantContext}
 * for compatibility with existing controllers.
 */
public class OAuth2RequestContextFilter extends OncePerRequestFilter {

    private final OAuth2SecurityProperties oauth2Properties;

    public OAuth2RequestContextFilter(OAuth2SecurityProperties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getAttribute("jwt.subject") != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String subject = JwtClaimSupport.userId(jwt, oauth2Properties.userIdClaim());
        if (subject == null || subject.isBlank()) {
            subject = jwt.getSubject();
        }
        String tenantId = JwtClaimSupport.tenantId(jwt, oauth2Properties.tenantClaim());
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = oauth2Properties.defaultTenantId();
        }
        List<String> roles = JwtClaimSupport.roles(jwt, oauth2Properties.rolesClaim());

        request.setAttribute("jwt.subject", subject);
        request.setAttribute("jwt.tenantId", tenantId);
        request.setAttribute("jwt.roles", roles);
        request.setAttribute("request.source", "WEB");

        boolean tenantSet = false;
        if (tenantId != null) {
            TenantContext.set(tenantId);
            MDC.put(TraceKeys.TENANT_ID, tenantId);
            tenantSet = true;
        }
        MDC.put("principal", subject != null ? subject : "anonymous");

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
                MDC.remove(TraceKeys.TENANT_ID);
            }
            MDC.remove("principal");
        }
    }
}
