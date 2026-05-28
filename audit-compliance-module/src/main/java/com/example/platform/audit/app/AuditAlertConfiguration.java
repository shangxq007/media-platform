package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for audit security alerts.
 *
 * <p>Creates the appropriate {@link SecurityAlertPort} adapter based on
 * {@code audit.alerts.publisher.type} configuration:
 * <ul>
 *   <li>{@code slf4j} (default) — publish to SLF4J structured logs</li>
 *   <li>{@code noop} — discard all alerts</li>
 *   <li>{@code webhook} — HTTP POST to configured URL with SSRF protection</li>
 * </ul>
 *
 * <p>Fail-fast: if {@code type=webhook} and URL is blank, invalid, or blocked
 * by SSRF rules, the application will fail to start with a clear error message.
 */
@Configuration
@EnableConfigurationProperties(AuditAlertProperties.class)
public class AuditAlertConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditAlertConfiguration.class);

    @Bean
    public SecurityAlertPort securityAlertPort(AuditAlertProperties properties) {
        String type = properties.publisher() != null ? properties.publisher().type() : "slf4j";

        return switch (type.toLowerCase()) {
            case "slf4j" -> {
                log.info("Security alert publisher: SLF4J (structured logs)");
                yield new Slf4jSecurityAlertAdapter();
            }
            case "noop" -> {
                log.info("Security alert publisher: NOOP (alerts discarded)");
                yield new NoopSecurityAlertAdapter();
            }
            case "webhook" -> {
                AuditAlertProperties.Webhook wh = properties.publisher().webhook();
                if (wh == null || wh.url() == null || wh.url().isBlank()) {
                    throw new IllegalStateException(
                            "audit.alerts.publisher.type=webhook requires " +
                            "audit.alerts.publisher.webhook.url to be set");
                }

                // Create URL validator with SSRF protection
                WebhookUrlValidator validator = new WebhookUrlValidator(
                        wh.allowPrivateNetwork(),
                        wh.allowedHosts(),
                        wh.allowedDomainSuffixes());

                log.info("Security alert publisher: Webhook (host={})",
                        WebhookUrlValidator.extractHost(wh.url()));
                yield new WebhookSecurityAlertAdapter(
                        wh.url(), wh.connectTimeoutMs(), wh.readTimeoutMs(),
                        wh.authorizationHeader(), validator);
            }
            default -> {
                throw new IllegalStateException(
                        "Unknown audit.alerts.publisher.type: '" + type + "'. " +
                        "Supported: slf4j, noop, webhook");
            }
        };
    }
}
