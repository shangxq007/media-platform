package com.example.platform.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Conditionally sets JVM system properties for HTTP/HTTPS proxy.
 *
 * <p>Only activated when {@code egress.proxy.jvm.enabled=true}.
 * This is a fallback for Java HTTP clients that do NOT read
 * {@code HTTP_PROXY}/{@code HTTPS_PROXY} environment variables
 * (e.g., {@code java.net.http.HttpClient}, Apache HttpClient).
 *
 * <p>Spring's {@code RestTemplate} with {@code SimpleClientHttpRequestFactory}
 * respects {@code http.proxyHost}/{@code http.proxyPort} system properties.
 * AWS SDK v2 respects these system properties as well.
 *
 * <p>WARNING: Affects ALL JVM HTTP connections in this process.
 * Test thoroughly in staging before enabling in production.
 */
@Configuration
@ConditionalOnProperty(prefix = "egress.proxy.jvm", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EgressProxySmokeProperties.class)
public class EgressProxyJvmProxyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EgressProxyJvmProxyConfiguration.class);

    private final EgressProxySmokeProperties properties;

    public EgressProxyJvmProxyConfiguration(EgressProxySmokeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void configureProxy() {
        EgressProxySmokeProperties.Jvm jvm = properties.getJvm();
        String host = jvm.getHost();
        int port = jvm.getPort();
        String nonProxyHosts = jvm.getNonProxyHosts();

        setIfAbsent("http.proxyHost", host);
        setIfAbsent("http.proxyPort", String.valueOf(port));
        setIfAbsent("https.proxyHost", host);
        setIfAbsent("https.proxyPort", String.valueOf(port));
        setIfAbsent("http.nonProxyHosts", nonProxyHosts);

        log.info("JVM proxy configured: host={} port={} nonProxyHosts={}", host, port, nonProxyHosts);
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
