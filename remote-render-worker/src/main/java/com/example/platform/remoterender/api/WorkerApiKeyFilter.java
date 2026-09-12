package com.example.platform.remoterender.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WorkerApiKeyFilter implements Filter {

    @Value("${app.remote-worker.api-key:}")
    private String configuredApiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            ((HttpServletResponse) response).sendError(503, "Worker credentials are not configured");
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String providedKey = httpRequest.getHeader("X-Worker-Api-Key");

        if (providedKey != null && java.security.MessageDigest.isEqual(
                configuredApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid or missing worker API key\"}");
        }
    }
}
