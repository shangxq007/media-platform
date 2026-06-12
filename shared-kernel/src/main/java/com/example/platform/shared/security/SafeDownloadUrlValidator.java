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
 *
 * <p><b>Known limitation:</b> DNS rebinding / TOCTOU attacks cannot be fully prevented by
 * pre-resolution validation alone. The DNS resolution and the actual HTTP connection are
 * separate steps — an attacker can serve a safe IP during validation and a private IP during
 * connection. Production deployments should additionally use:
 * <ul>
 *   <li>An egress proxy that pins resolved IPs</li>
 *   <li>Network policies that block private IP ranges at the network layer</li>
 *   <li>Connect-time IP validation where possible</li>
 * </ul>
 */
public final class SafeDownloadUrlValidator {

    private static final int MAX_URL_LENGTH = 8192;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static volatile DnsResolver dnsResolver = InetAddress::getAllByName;

    private SafeDownloadUrlValidator() {}

    /**
     * Configure the DNS resolver used for URL validation.
     * The default resolver uses {@link InetAddress#getAllByName(String)}.
     *
     * @param resolver the resolver to use (must not be null)
     */
    public static void setDnsResolver(DnsResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("DnsResolver must not be null");
        dnsResolver = resolver;
    }

    /**
     * Reset to the default system DNS resolver.
     */
    public static void resetDnsResolver() {
        dnsResolver = InetAddress::getAllByName;
    }

    /**
     * Validate a download URL for safety. Returns null if safe, error message if unsafe.
     *
     * <p>When DNS resolution fails, the URL is rejected (fail-closed).
     */
    public static String validate(String url) {
        return validate(url, dnsResolver);
    }

    /**
     * Validate a download URL with an explicit DNS resolver.
     * This overload is intended for testing.
     */
    static String validate(String url, DnsResolver resolver) {
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

        // Always check if host is a literal IP address
        try {
            InetAddress addr = InetAddress.getByName(host);
            String ipCheck = validateInetAddress(addr);
            if (ipCheck != null) return ipCheck;
        } catch (UnknownHostException e) {
            // Not a literal IP, that's OK — will be resolved below
        }

        // DNS resolution — fail closed
        try {
            InetAddress[] addrs = resolver.resolve(host);
            if (addrs == null || addrs.length == 0) {
                return "DNS resolution returned no addresses for host: " + host;
            }
            for (InetAddress addr : addrs) {
                String ipCheck = validateInetAddress(addr);
                if (ipCheck != null) {
                    return "Resolved IP " + addr.getHostAddress() + " is not allowed: " + ipCheck;
                }
            }
        } catch (Exception e) {
            return "DNS resolution failed for host: " + host;
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
            // 100.64.0.0/10 (Carrier-Grade NAT)
            if (a == 100 && b >= 64 && b <= 127) return "carrier-grade NAT 100.64.0.0/10 not allowed";
            // 198.18.0.0/15 (benchmarking)
            if (a == 198 && (b == 18 || b == 19)) return "benchmarking 198.18.0.0/15 not allowed";
        }

        // IPv6 checks
        if (ip.length == 16) {
            // fc00::/7 (unique local)
            if ((ip[0] & 0xFE) == 0xFC) return "unique local IPv6 fc00::/7 not allowed";
            // fe80::/10 (link-local) - already checked by isLinkLocalAddress
        }

        return null; // Safe
    }

    /**
     * Functional interface for DNS resolution. Allows injection of fake resolvers for testing.
     */
    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws Exception;
    }
}
