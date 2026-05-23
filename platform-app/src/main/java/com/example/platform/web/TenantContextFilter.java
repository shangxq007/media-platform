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
 * Establishes {@link TenantContext} from {@code X-Tenant-ID} when JWT did not set it
 * (e.g. local dev with {@code app.security.enabled=false}).
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final boolean allowTenantHeaderFallback;

    public TenantContextFilter() {
        this(true);
    }

    public TenantContextFilter(boolean allowTenantHeaderFallback) {
        this.allowTenantHeaderFallback = allowTenantHeaderFallback;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (allowTenantHeaderFallback && TenantContext.get() == null) {
            String tenantId = request.getHeader(TENANT_HEADER);
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId.trim());
                MDC.put(TraceKeys.TENANT_ID, tenantId.trim());
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TraceKeys.TENANT_ID);
        }
    }
}
