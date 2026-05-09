package com.example.platform.identity.app;

import com.example.platform.shared.logging.TraceKeys;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/v1/extensions",
            "/api/v1/audit",
            "/api/v1/outbox",
            "/api/v1/render",
            "/api/v1/storage",
            "/api/v1/identity"
    );

    private final IdentityAccessService identityAccessService;
    private final IdentityProperties identityProperties;

    public ApiKeyAuthFilter(IdentityAccessService identityAccessService, IdentityProperties identityProperties) {
        this.identityAccessService = identityAccessService;
        this.identityProperties = identityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!identityProperties.isApiKeyAuthEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Missing API key");
            pd.setTitle(CommonErrorCode.AUTHENTICATION_REQUIRED.title());
            pd.setProperty("code", CommonErrorCode.AUTHENTICATION_REQUIRED.code());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"" + pd.getTitle()
                            + "\",\"status\":401,\"detail\":\"" + pd.getDetail()
                            + "\",\"code\":\"" + CommonErrorCode.AUTHENTICATION_REQUIRED.code() + "\"}");
            return;
        }

        if (!identityAccessService.validateApiKey(apiKey)) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
            pd.setTitle(CommonErrorCode.AUTHENTICATION_REQUIRED.title());
            pd.setProperty("code", CommonErrorCode.AUTHENTICATION_REQUIRED.code());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"" + pd.getTitle()
                            + "\",\"status\":401,\"detail\":\"" + pd.getDetail()
                            + "\",\"code\":\"" + CommonErrorCode.AUTHENTICATION_REQUIRED.code() + "\"}");
            return;
        }

        String tenantId = identityAccessService.tenantIdOf(apiKey);
        String principal = identityAccessService.principalOf(apiKey);
        try {
            if (tenantId != null) {
                TenantContext.set(tenantId);
                MDC.put(TraceKeys.TENANT_ID, tenantId);
            }
            if (principal != null) {
                MDC.put("principal", principal);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TraceKeys.TENANT_ID);
            MDC.remove("principal");
        }
    }
}
