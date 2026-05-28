package com.example.platform.audit.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Webhook adapter that POSTs security alerts as JSON to a configured URL.
 *
 * <p>Configuration:
 * <pre>
 * audit.alerts.publisher.type=webhook
 * audit.alerts.publisher.webhook.url=https://your-alert-gateway/alerts
 * audit.alerts.publisher.webhook.connect-timeout-ms=1000
 * audit.alerts.publisher.webhook.read-timeout-ms=3000
 * audit.alerts.publisher.webhook.authorization-header=Bearer xxx
 * audit.alerts.publisher.webhook.allow-private-network=false
 * audit.alerts.publisher.webhook.allowed-hosts=alerts.example.com
 * audit.alerts.publisher.webhook.allowed-domain-suffixes=.alerts.example.com
 * </pre>
 *
 * <p>Security guarantees:
 * <ul>
 *   <li>URL validated against SSRF blocklist and allowlist at construction time</li>
 *   <li>Authorization header sent but NOT logged</li>
 *   <li>Request body NOT logged</li>
 *   <li>HTTP errors logged without sensitive content</li>
 *   <li>Publish failure never blocks AuditService.record()</li>
 * </ul>
 */
public class WebhookSecurityAlertAdapter implements SecurityAlertPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityAlertAdapter.class);

    private final String webhookUrl;
    private final String authorizationHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String urlHost; // for safe logging (no path/query)

    public WebhookSecurityAlertAdapter(String webhookUrl, int connectTimeoutMs, int readTimeoutMs,
                                        String authorizationHeader,
                                        WebhookUrlValidator urlValidator) {
        // Validate URL through SSRF protection
        String validatedHost = urlValidator.validate(webhookUrl);

        this.webhookUrl = webhookUrl;
        this.authorizationHeader = (authorizationHeader != null && !authorizationHeader.isBlank())
                ? authorizationHeader : null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 100)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.urlHost = validatedHost;

        log.info("WebhookSecurityAlertAdapter created: host={} connectTimeoutMs={} readTimeoutMs={} auth={}",
                urlHost, connectTimeoutMs, readTimeoutMs,
                this.authorizationHeader != null ? "configured" : "none");
    }

    @Override
    public void publish(SecurityAlert alert) {
        try {
            String jsonBody = objectMapper.writeValueAsString(alert);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

            if (authorizationHeader != null) {
                builder.header("Authorization", authorizationHeader);
            }

            HttpRequest httpRequest = builder.build();

            try {
                HttpResponse<String> response = httpClient.send(httpRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    log.debug("Webhook alert published: rule={} status={}", alert.rule(), status);
                } else {
                    log.warn("Webhook alert failed: rule={} severity={} status={} host={}",
                            alert.rule(), alert.severity(), status, urlHost);
                }
            } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
                log.warn("Webhook alert connection failed: rule={} host={} error={}",
                        alert.rule(), urlHost, e.getClass().getSimpleName());
            } catch (java.net.http.HttpTimeoutException e) {
                log.warn("Webhook alert timeout: rule={} host={}", alert.rule(), urlHost);
            } catch (java.io.IOException e) {
                log.warn("Webhook alert I/O error: rule={} host={} error={}",
                        alert.rule(), urlHost, e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Webhook alert interrupted: rule={}", alert.rule());
            }

        } catch (Exception e) {
            log.warn("Webhook alert publish failed: rule={} error={}", alert.rule(), e.getMessage());
        }
    }
}
