package com.example.platform.production;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for egress proxy smoke test and JVM proxy options.
 *
 * <p>The smoke test verifies that external HTTP(S) requests from the platform
 * can reach a configured URL through the egress proxy. This is critical because
 * {@code java.net.http.HttpClient} does NOT automatically read {@code HTTP_PROXY}
 * / {@code HTTPS_PROXY} environment variables.
 *
 * <p>Defaults are safe: smoke disabled, JVM proxy disabled.
 * Enable in staging first to validate proxy behavior before production.
 */
@ConfigurationProperties(prefix = "egress.proxy")
public class EgressProxySmokeProperties {

    private Smoke smoke = new Smoke();
    private Jvm jvm = new Jvm();

    public Smoke getSmoke() { return smoke; }
    public void setSmoke(Smoke smoke) { this.smoke = smoke; }
    public Jvm getJvm() { return jvm; }
    public void setJvm(Jvm jvm) { this.jvm = jvm; }

    public static class Smoke {
        /** Enable the egress proxy smoke test. */
        private boolean enabled = false;

        /**
         * URL to request during the smoke test.
         * Must be an http/https URL. Must be in Squid allowed-domains.
         * Must NOT contain secrets or query tokens.
         * Only read from configuration — never from request parameters.
         */
        private String url = "";

        /** Connect + read timeout for the smoke request. */
        private int timeoutMs = 3000;

        /** Expected HTTP status code for a successful smoke test. */
        private int expectedStatus = 200;

        /**
         * Include smoke test result in the readiness health group.
         * Default false — external dependency volatility should not block Pod readiness.
         */
        private boolean includeInReadiness = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getExpectedStatus() { return expectedStatus; }
        public void setExpectedStatus(int expectedStatus) { this.expectedStatus = expectedStatus; }
        public boolean isIncludeInReadiness() { return includeInReadiness; }
        public void setIncludeInReadiness(boolean includeInReadiness) { this.includeInReadiness = includeInReadiness; }
    }

    public static class Jvm {
        /**
         * Enable JVM system properties for proxy configuration.
         * When true, sets http.proxyHost, http.proxyPort, https.proxyHost,
         * https.proxyPort, http.nonProxyHosts on JVM startup.
         *
         * <p>Only enable if Java HTTP clients do not honor HTTP_PROXY env vars.
         * Affects ALL JVM HTTP connections in this process.
         */
        private boolean enabled = false;

        /** Proxy host. */
        private String host = "egress-proxy";

        /** Proxy port. */
        private int port = 3128;

        /**
         * Pipe-separated list of hosts that bypass the proxy.
         * Java nonProxyHosts syntax: {@code localhost|127.*|*.svc|*.cluster.local}
         */
        private String nonProxyHosts = "localhost|127.*|*.svc|*.cluster.local|postgresql|minio|platform-api|sandbox-worker|egress-proxy";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getNonProxyHosts() { return nonProxyHosts; }
        public void setNonProxyHosts(String nonProxyHosts) { this.nonProxyHosts = nonProxyHosts; }
    }
}
