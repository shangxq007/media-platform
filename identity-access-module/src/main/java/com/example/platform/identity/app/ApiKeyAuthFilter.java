package com.example.platform.identity.app;

import com.example.platform.observability.context.TraceKeys;
import com.example.platform.identity.authorization.ApiKeyCanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Set<String> PROTECTED_PREFIXES = Set.of(
            "/api/extensions",
            "/api/audit",
            "/api/outbox",
            "/api/render",
            "/api/storage",
            "/api/identity"
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
        request.removeAttribute(ApiKeyCanonicalActorResolver.AUTHENTICATED_ACTOR_ATTRIBUTE);

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
            if (principal != null && !principal.isBlank() && tenantId != null && !tenantId.isBlank()) {
                request.setAttribute(ApiKeyCanonicalActorResolver.AUTHENTICATED_ACTOR_ATTRIBUTE,
                        CanonicalActor.apiKey(principal, tenantId, Set.of(), "api-key"));
            }
            if (tenantId != null) {
                TenantContext.set(tenantId);
                MDC.put(TraceKeys.TENANT_ID, tenantId);
            }
            if (principal != null) {
                MDC.put("principal", principal);
            }
            filterChain.doFilter(request, response);
        } finally {
            request.removeAttribute(ApiKeyCanonicalActorResolver.AUTHENTICATED_ACTOR_ATTRIBUTE);
            TenantContext.clear();
            MDC.remove(TraceKeys.TENANT_ID);
            MDC.remove("principal");
        }
    }
}
