package com.example.platform.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smoke test service that verifies external HTTP(S) requests can reach
 * a configured URL. Uses {@code java.net.http.HttpClient} to match the
 * same HTTP client used by {@code WebhookSecurityAlertAdapter}.
 *
 * <p>This test intentionally uses the same client as production code
 * so that if the client doesn't honor proxy env vars, the smoke test
 * will fail — surfacing the problem before it affects real traffic.
 *
 * <p>The smoke URL is ONLY read from configuration.
 * No request parameter can override it (no SSRF vector).
 */
@Service
public class EgressProxySmokeService {

    private static final Logger log = LoggerFactory.getLogger(EgressProxySmokeService.class);

    private final EgressProxySmokeProperties properties;

    public EgressProxySmokeService(EgressProxySmokeProperties properties) {
        this.properties = properties;
    }

    /**
     * Execute the smoke test. Safe to call from HealthIndicator or admin endpoint.
     *
     * @return smoke test result with status, target host, duration, and optional error
     */
    public SmokeResult execute() {
        EgressProxySmokeProperties.Smoke smoke = properties.getSmoke();

        if (!smoke.isEnabled()) {
            return SmokeResult.disabled();
        }

        String url = smoke.getUrl();
        if (url == null || url.isBlank()) {
            return SmokeResult.configError("smoke URL is not configured");
        }

        // Basic URL validation — must be http/https, no userinfo
        String targetHost;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return SmokeResult.configError("smoke URL must use http or https scheme");
            }
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                return SmokeResult.configError("smoke URL must not contain userinfo");
            }
            targetHost = uri.getHost();
            if (targetHost == null || targetHost.isBlank()) {
                return SmokeResult.configError("smoke URL must have a host");
            }
            // Block metadata IP literal
            if ("169.254.169.254".equals(targetHost)) {
                return SmokeResult.configError("smoke URL must not be metadata IP");
            }
            // Block localhost/loopback
            String hostLower = targetHost.toLowerCase();
            if (hostLower.equals("localhost") || hostLower.equals("127.0.0.1")
                    || hostLower.equals("::1") || hostLower.equals("0.0.0.0")) {
                return SmokeResult.configError("smoke URL must not be localhost/loopback");
            }
        } catch (IllegalArgumentException e) {
            return SmokeResult.configError("smoke URL is not a valid URI: " + e.getMessage());
        }

        int timeoutMs = Math.max(smoke.getTimeoutMs(), 500);
        int expectedStatus = smoke.getExpectedStatus();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();

        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - start;
            int statusCode = response.statusCode();

            if (statusCode == expectedStatus) {
                log.debug("Egress proxy smoke test passed: host={} status={} durationMs={}",
                        targetHost, statusCode, durationMs);
                return SmokeResult.success(targetHost, statusCode, durationMs);
            } else {
                log.warn("Egress proxy smoke test failed: host={} expectedStatus={} actualStatus={} durationMs={}",
                        targetHost, expectedStatus, statusCode, durationMs);
                return SmokeResult.failed(targetHost, statusCode, durationMs,
                        "unexpected status: " + statusCode + " (expected " + expectedStatus + ")");
            }
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Egress proxy smoke test connection failed: host={} error={} durationMs={}",
                    targetHost, e.getClass().getSimpleName(), durationMs);
            return SmokeResult.failed(targetHost, 0, durationMs, "connection failed: " + e.getClass().getSimpleName());
        } catch (java.net.http.HttpTimeoutException e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Egress proxy smoke test timeout: host={} durationMs={}", targetHost, durationMs);
            return SmokeResult.failed(targetHost, 0, durationMs, "timeout");
        } catch (java.io.IOException e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Egress proxy smoke test I/O error: host={} error={}", targetHost, e.getClass().getSimpleName());
            return SmokeResult.failed(targetHost, 0, durationMs, "I/O error: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Egress proxy smoke test interrupted: host={}", targetHost);
            return SmokeResult.failed(targetHost, 0, durationMs, "interrupted");
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Egress proxy smoke test unexpected error: host={} error={}", targetHost, e.getClass().getSimpleName());
            return SmokeResult.failed(targetHost, 0, durationMs, "error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Immutable result of a smoke test execution.
     * Does NOT include response body, query parameters, or secrets.
     */
    public record SmokeResult(
            Status status,
            String targetHost,
            int statusCode,
            long durationMs,
            String error
    ) {
        public enum Status { DISABLED, SUCCESS, FAILED, CONFIG_ERROR }

        static SmokeResult disabled() {
            return new SmokeResult(Status.DISABLED, null, 0, 0, null);
        }

        static SmokeResult configError(String error) {
            return new SmokeResult(Status.CONFIG_ERROR, null, 0, 0, error);
        }

        static SmokeResult success(String targetHost, int statusCode, long durationMs) {
            return new SmokeResult(Status.SUCCESS, targetHost, statusCode, durationMs, null);
        }

        static SmokeResult failed(String targetHost, int statusCode, long durationMs, String error) {
            return new SmokeResult(Status.FAILED, targetHost, statusCode, durationMs, error);
        }

        /**
         * Convert to a Map suitable for HealthIndicator details.
         * Excludes response body and full URL (no query/secret leakage).
         */
        public Map<String, Object> toHealthDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", status.name());
            if (targetHost != null) {
                details.put("targetHost", targetHost);
            }
            if (statusCode > 0) {
                details.put("statusCode", statusCode);
            }
            if (durationMs > 0) {
                details.put("durationMs", durationMs);
            }
            if (error != null) {
                details.put("error", error);
            }
            return details;
        }
    }
}
