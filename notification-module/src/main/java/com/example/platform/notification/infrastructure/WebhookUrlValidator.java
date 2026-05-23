package com.example.platform.notification.infrastructure;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.util.List;

@Component
public class WebhookUrlValidator {
    private static final Logger log = LoggerFactory.getLogger(WebhookUrlValidator.class);

    private final List<String> allowlist;
    private final List<String> blocklist;

    public WebhookUrlValidator(
            @Value("${app.notification.webhook.allowlist:#{T(java.util.Collections).emptyList()}}") List<String> allowlist,
            @Value("${app.notification.webhook.blocklist:#{T(java.util.Collections).emptyList()}}") List<String> blocklist) {
        this.allowlist = allowlist;
        this.blocklist = blocklist;
    }

    public void validate(String url, ConfigurableErrorCode invalidError, ConfigurableErrorCode blockedError) {
        URI uri;
        try {
            uri = new URL(url).toURI();
        } catch (Exception e) {
            log.warn("Webhook URL parse failure: {}", e.getMessage());
            throw new PlatformException(invalidError, "Invalid webhook URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            log.warn("Webhook URL rejected: non-HTTP scheme '{}'", scheme);
            throw new PlatformException(invalidError, "Webhook URL must use http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new PlatformException(invalidError, "Webhook URL has no host");
        }

        for (String blocked : blocklist) {
            if (host.equalsIgnoreCase(blocked) || host.endsWith("." + blocked)) {
                log.warn("Webhook URL rejected: host '{}' matches blocklist", host);
                throw new PlatformException(blockedError, "Webhook URL host is blocklisted: " + host);
            }
        }

        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                log.warn("Webhook URL rejected: private/loopback address '{}'", host);
                throw new PlatformException(blockedError, "Webhook URL resolves to a private/internal IP");
            }
            if (host.startsWith("169.254.")) {
                log.warn("Webhook URL rejected: link-local metadata address '{}'", host);
                throw new PlatformException(blockedError, "Webhook URL resolves to a link-local/metadata address");
            }
        } catch (java.net.UnknownHostException e) {
            log.warn("Webhook URL rejected: cannot resolve host '{}'", host);
            throw new PlatformException(invalidError, "Cannot resolve webhook URL host: " + host);
        }
    }
}
