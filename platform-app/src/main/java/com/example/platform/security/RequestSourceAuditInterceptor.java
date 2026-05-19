package com.example.platform.security;

import com.example.platform.shared.audit.AuditPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = false)
public class RequestSourceAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestSourceAuditInterceptor.class);

    private final AuditPort auditPort;

    public RequestSourceAuditInterceptor(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String source = resolveSource(request);
        request.setAttribute("request.source", source);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String tenantId = MDC.get("tenantId");
        String principal = MDC.get("principal");
        String traceId = MDC.get("traceId");

        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("source", source);
        auditPayload.put("method", method);
        auditPayload.put("path", sanitizePath(path));
        if (tenantId != null) auditPayload.put("tenantId", tenantId);
        if (principal != null) auditPayload.put("principal", principal);
        if (traceId != null) auditPayload.put("traceId", traceId);

        auditPort.record("USER", "REQUEST_RECEIVED", "API_REQUEST",
                "http_request", method + " " + sanitizePath(path),
                auditPayload);

        return true;
    }

    private String resolveSource(HttpServletRequest request) {
        String existing = (String) request.getAttribute("request.source");
        if (existing != null) return existing;

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/mcp/")) return "MCP";
        if (path.startsWith("/api/v1/")) return "WEB";
        return "UNKNOWN";
    }

    private String sanitizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{id}");
    }
}
