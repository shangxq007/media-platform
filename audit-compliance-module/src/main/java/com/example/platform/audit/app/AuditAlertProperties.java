package com.example.platform.audit.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for audit security alert rules.
 */
@ConfigurationProperties(prefix = "audit.alerts")
public record AuditAlertProperties(
    /**
     * Denied burst detection configuration.
     */
    DeniedBurst deniedBurst,

    /**
     * Security alert publisher configuration.
     */
    Publisher publisher
) {
    public AuditAlertProperties {
        if (deniedBurst == null) deniedBurst = DeniedBurst.defaults();
        if (publisher == null) publisher = Publisher.defaults();
    }

    public static AuditAlertProperties defaults() {
        return new AuditAlertProperties(DeniedBurst.defaults(), Publisher.defaults());
    }

    /**
     * Configuration for the ADMIN_DENIED_BURST aggregated alert rule.
     */
    public record DeniedBurst(
        /** Whether burst detection is enabled. */
        boolean enabled,
        /** Number of denied events in the window to trigger alert. */
        int threshold,
        /** Sliding window duration in seconds. */
        long windowSeconds,
        /** Cooldown after alert before re-alerting, in seconds. */
        long cooldownSeconds,
        /** Maximum number of tracked actors to prevent memory exhaustion. */
        int maxActors
    ) {
        public DeniedBurst {
            if (threshold < 2) threshold = 2;
            if (windowSeconds <= 0) windowSeconds = 600;   // 10 minutes
            if (cooldownSeconds < 0) cooldownSeconds = 1800; // 30 minutes
            if (maxActors <= 0) maxActors = 10000;
        }

        public static DeniedBurst defaults() {
            return new DeniedBurst(true, 5, 600, 1800, 10000);
        }
    }

    /**
     * Configuration for the security alert publisher.
     *
     * <p>Supported types:
     * <ul>
     *   <li>{@code slf4j} (default) — publish to SLF4J structured logs</li>
     *   <li>{@code noop} — discard all alerts</li>
     *   <li>{@code webhook} — HTTP POST to configured URL</li>
     * </ul>
     */
    public record Publisher(
        /** Publisher type: slf4j, noop, or webhook. */
        String type,
        /** Webhook configuration (only used when type=webhook). */
        Webhook webhook
    ) {
        public Publisher {
            if (type == null || type.isBlank()) type = "slf4j";
            if (webhook == null) webhook = Webhook.defaults();
        }

        public static Publisher defaults() {
            return new Publisher("slf4j", Webhook.defaults());
        }
    }

    /**
     * Webhook configuration for security alert publishing.
     */
    public record Webhook(
        /** Webhook URL (required when type=webhook). */
        String url,
        /** HTTP connect timeout in milliseconds. */
        int connectTimeoutMs,
        /** HTTP read timeout in milliseconds. */
        int readTimeoutMs,
        /** Authorization header value (e.g. "Bearer xxx"). NOT logged. */
        String authorizationHeader,
        /** Whether to allow private network IPs. Default false (blocks private/localhost/link-local). */
        boolean allowPrivateNetwork,
        /** Comma-separated exact hostnames allowed. If empty, uses SSRF blocklist only. */
        java.util.List<String> allowedHosts,
        /** Comma-separated domain suffixes allowed (must start with '.'). */
        java.util.List<String> allowedDomainSuffixes
    ) {
        public Webhook {
            if (url == null) url = "";
            if (connectTimeoutMs <= 0) connectTimeoutMs = 1000;
            if (readTimeoutMs <= 0) readTimeoutMs = 3000;
            if (authorizationHeader == null) authorizationHeader = "";
            if (allowedHosts == null) allowedHosts = java.util.List.of();
            if (allowedDomainSuffixes == null) allowedDomainSuffixes = java.util.List.of();
        }

        public static Webhook defaults() {
            return new Webhook("", 1000, 3000, "", false,
                    java.util.List.of(), java.util.List.of());
        }
    }
}
