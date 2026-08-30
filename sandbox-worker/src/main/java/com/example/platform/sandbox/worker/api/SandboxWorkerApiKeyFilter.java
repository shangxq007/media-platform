package com.example.platform.sandbox.worker.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fail-closed service authentication for the standalone sandbox worker. */
@Component
public class SandboxWorkerApiKeyFilter implements Filter {

    static final String API_KEY_HEADER = "X-Sandbox-Worker-Api-Key";

    @Value("${app.sandbox-worker.api-key:}")
    private String configuredApiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (isHealthProbe(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            reject(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Sandbox worker API key is not configured");
            return;
        }

        if (!configuredApiKey.equals(httpRequest.getHeader(API_KEY_HEADER))) {
            reject(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or missing sandbox worker API key");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isHealthProbe(HttpServletRequest request) {
        return "/v1/sandbox/healthz".equals(request.getRequestURI())
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
    }

    private static void reject(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
