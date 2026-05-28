package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

/**
 * Validates webhook URLs to prevent SSRF / internal network probing.
 *
 * <p>Default behavior (no allowlist configured):
 * <ul>
 *   <li>Only http/https schemes</li>
 *   <li>No userinfo (user:pass@host)</li>
 *   <li>Blocks localhost, loopback, link-local, metadata IP, private networks</li>
 *   <li>Allows public hostnames and IPs</li>
 * </ul>
 *
 * <p>With allowlist:
 * <ul>
 *   <li>URL host must match allowedHosts or allowedDomainSuffixes</li>
 *   <li>SSRF blocklist still applies</li>
 *   <li>Metadata IP always blocked even with allowPrivateNetwork=true</li>
 * </ul>
 *
 * <p>This class does NOT do DNS resolution — it only checks the hostname/IP literal
 * in the URL. DNS rebinding protection requires egress network policy or DNS pinning.
 */
public class WebhookUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookUrlValidator.class);

    private static final String METADATA_IP = "169.254.169.254";

    private final boolean allowPrivateNetwork;
    private final Set<String> allowedHosts;
    private final List<String> allowedDomainSuffixes;

    public WebhookUrlValidator(boolean allowPrivateNetwork,
                                List<String> allowedHosts,
                                List<String> allowedDomainSuffixes) {
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.allowedHosts = allowedHosts != null ?
                allowedHosts.stream().map(String::toLowerCase).map(String::trim)
                        .collect(java.util.stream.Collectors.toSet()) : Set.of();
        this.allowedDomainSuffixes = allowedDomainSuffixes != null ?
                allowedDomainSuffixes.stream().map(String::toLowerCase).map(String::trim)
                        .collect(java.util.stream.Collectors.toUnmodifiableList()) : List.of();
    }

    /**
     * Validate a webhook URL. Throws IllegalArgumentException if rejected.
     *
     * @param url the webhook URL to validate
     * @return the sanitized URI host (lowercase, no port) for safe logging
     */
    public String validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook URL is not a valid URI: " + e.getMessage());
        }

        // Scheme check
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Webhook URL must use http or https scheme");
        }

        // Userinfo check — no user:pass@host
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not contain userinfo (user:pass@host)");
        }

        // Host check
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must have a host");
        }

        String hostLower = host.toLowerCase().trim();
        // Remove trailing dot
        if (hostLower.endsWith(".")) {
            hostLower = hostLower.substring(0, hostLower.length() - 1);
        }

        // Allowlist check (if configured)
        if (!allowedHosts.isEmpty() || !allowedDomainSuffixes.isEmpty()) {
            if (!isHostAllowed(hostLower)) {
                throw new IllegalArgumentException(
                        "Webhook URL host '" + hostLower + "' is not in the allowed hosts list");
            }
        }

        // SSRF blocklist check
        if (isBlockedHost(hostLower)) {
            throw new IllegalArgumentException(
                    "Webhook URL host '" + hostLower + "' is blocked (SSRF protection)");
        }

        // IP literal check
        if (isIpLiteral(hostLower)) {
            try {
                InetAddress addr = InetAddress.getByName(hostLower);
                if (isBlockedAddress(addr)) {
                    throw new IllegalArgumentException(
                            "Webhook URL IP '" + hostLower + "' is blocked (SSRF protection)");
                }
            } catch (UnknownHostException e) {
                // If we can't resolve, block it
                throw new IllegalArgumentException(
                        "Webhook URL host '" + hostLower + "' cannot be resolved");
            }
        }

        return hostLower;
    }

    private boolean isHostAllowed(String host) {
        // Exact match
        if (allowedHosts.contains(host)) {
            return true;
        }
        // Domain suffix match
        for (String suffix : allowedDomainSuffixes) {
            if (suffix.startsWith(".") && host.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedHost(String hostLower) {
        // localhost variants
        if (hostLower.equals("localhost") || hostLower.endsWith(".localhost")) {
            return true;
        }
        // 0.0.0.0
        if (hostLower.equals("0.0.0.0")) {
            return true;
        }
        // IPv6 loopback
        if (hostLower.equals("::1") || hostLower.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        return false;
    }

    private boolean isBlockedAddress(InetAddress addr) {
        // Metadata IP — always blocked
        if (METADATA_IP.equals(addr.getHostAddress())) {
            log.warn("Blocked webhook to metadata IP: {}", METADATA_IP);
            return true;
        }

        // Loopback (127.0.0.0/8, ::1)
        if (addr.isLoopbackAddress()) {
            return true;
        }

        // Link-local (169.254.0.0/16, fe80::/10)
        if (addr.isLinkLocalAddress()) {
            return true;
        }

        // Anylocal (0.0.0.0, ::)
        if (addr.isAnyLocalAddress()) {
            return true;
        }

        // Private networks (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fc00::/7)
        if (addr.isSiteLocalAddress() || addr.isMCSiteLocal()) {
            return !allowPrivateNetwork;
        }

        // IPv6 unique-local (fc00::/7)
        if (addr instanceof Inet6Address ipv6) {
            byte[] addr27 = ipv6.getAddress();
            if ((addr27[0] & 0xfe) == 0xfc) {
                return !allowPrivateNetwork;
            }
        }

        return false;
    }

    private static boolean isIpLiteral(String host) {
        // IPv4 literal
        if (host.chars().allMatch(c -> c == '.' || Character.isDigit(c))) {
            return true;
        }
        // IPv6 literal (contains colons)
        if (host.contains(":")) {
            return true;
        }
        return false;
    }

    /**
     * Extract host from URL for safe logging (no path/query/auth).
     */
    public static String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port > 0 && port != 80 && port != 443) {
                return host + ":" + port;
            }
            return host != null ? host : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
