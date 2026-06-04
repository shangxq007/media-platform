package com.example.platform.shared.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Validates download URLs to prevent SSRF attacks during project import.
 *
 * <p>Checks performed before any HTTP request is made:
 * <ul>
 *   <li>Scheme must be http or https</li>
 *   <li>Host must be present</li>
 *   <li>Resolved IPs must not be loopback, link-local, private, or multicast</li>
 *   <li>URL length must not exceed 8192 characters</li>
 * </ul>
 */
public final class SafeDownloadUrlValidator {

    private static final int MAX_URL_LENGTH = 8192;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static volatile boolean skipDnsResolution = false;

    private SafeDownloadUrlValidator() {}

    /**
     * Enable/disable DNS resolution check. Useful for testing without network access.
     */
    public static void setSkipDnsResolution(boolean skip) {
        skipDnsResolution = skip;
    }

    /**
     * Validate a download URL for safety. Returns null if safe, error message if unsafe.
     */
    public static String validate(String url) {
        if (url == null || url.isBlank()) {
            return "URL is null or blank";
        }
        if (url.length() > MAX_URL_LENGTH) {
            return "URL exceeds maximum length of " + MAX_URL_LENGTH;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "Invalid URL format: " + e.getMessage();
        }

        // Scheme check
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            return "URL scheme must be http or https, got: " + scheme;
        }

        // Host check
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL must have a host";
        }

        // Literal IP or hostname checks
        String hostLower = host.toLowerCase();
        if ("localhost".equals(hostLower)) {
            return "localhost is not allowed";
        }

        // Always check if host is a literal IP address (even when DNS is skipped)
        try {
            InetAddress addr = InetAddress.getByName(host);
            String ipCheck = validateInetAddress(addr);
            if (ipCheck != null) return ipCheck;
        } catch (UnknownHostException e) {
            // Not a literal IP, that's OK
        }

        // Skip DNS resolution if configured (e.g., in tests without network)
        if (!skipDnsResolution) {
            // Resolve all addresses and check each
            try {
                InetAddress[] addrs = InetAddress.getAllByName(host);
                for (InetAddress addr : addrs) {
                    String ipCheck = validateInetAddress(addr);
                    if (ipCheck != null) {
                        return "Resolved IP " + addr.getHostAddress() + " is not allowed: " + ipCheck;
                    }
                }
            } catch (UnknownHostException e) {
                return "DNS resolution failed for host: " + host;
            }
        }

        return null; // Safe
    }

    /**
     * Returns true if the URL is safe to download from.
     */
    public static boolean isSafe(String url) {
        return validate(url) == null;
    }

    private static String validateInetAddress(InetAddress addr) {
        if (addr.isLoopbackAddress()) {
            return "loopback address not allowed";
        }
        if (addr.isLinkLocalAddress()) {
            return "link-local address not allowed";
        }
        if (addr.isMulticastAddress()) {
            return "multicast address not allowed";
        }

        byte[] ip = addr.getAddress();

        // IPv4 checks
        if (ip.length == 4) {
            int a = ip[0] & 0xFF;
            int b = ip[1] & 0xFF;

            // 10.0.0.0/8
            if (a == 10) return "private network 10.0.0.0/8 not allowed";
            // 172.16.0.0/12
            if (a == 172 && b >= 16 && b <= 31) return "private network 172.16.0.0/12 not allowed";
            // 192.168.0.0/16
            if (a == 192 && b == 168) return "private network 192.168.0.0/16 not allowed";
            // 169.254.0.0/16
            if (a == 169 && b == 254) return "link-local 169.254.0.0/16 not allowed";
        }

        // IPv6 checks
        if (ip.length == 16) {
            // fc00::/7 (unique local)
            if ((ip[0] & 0xFE) == 0xFC) return "unique local IPv6 fc00::/7 not allowed";
            // fe80::/10 (link-local) - already checked by isLinkLocalAddress
        }

        return null; // Safe
    }
}
