package com.example.platform.shared.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Objects;
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
 * <p><b>Instance isolation:</b> Each validator holds its own immutable {@link DnsResolver}.
 * Tests inject a fake resolver via the constructor; production uses the default system DNS.
 * There is no global mutable resolver state.
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

    /**
     * Stateless, immutable system DNS resolver shared across default instances.
     * Not runtime-replaceable — there is no setter.
     */
    private static final DnsResolver SYSTEM_RESOLVER = InetAddress::getAllByName;

    /**
     * Immutable default instance using system DNS. Used by the static facade methods
     * to preserve backward compatibility for existing callers.
     */
    private static final SafeDownloadUrlValidator DEFAULT = new SafeDownloadUrlValidator(SYSTEM_RESOLVER);

    /** This instance's DNS resolver — final, immutable after construction. */
    private final DnsResolver resolver;

    /**
     * Create a validator using the system DNS resolver.
     */
    public SafeDownloadUrlValidator() {
        this(SYSTEM_RESOLVER);
    }

    /**
     * Create a validator with a specific DNS resolver.
     *
     * @param resolver the resolver to use (must not be null)
     */
    public SafeDownloadUrlValidator(DnsResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "DnsResolver must not be null");
    }

    /**
     * Validate a download URL for safety using this instance's resolver.
     * Returns null if safe, error message if unsafe.
     *
     * <p>When DNS resolution fails, the URL is rejected (fail-closed).
     */
    public String validateUrl(String url) {
        return doValidate(url, this.resolver);
    }

    /**
     * Returns true if the URL is safe to download from.
     */
    public boolean isSafeUrl(String url) {
        return validateUrl(url) == null;
    }

    // ---- Static backward-compatible facade ----
    // These delegate to an immutable DEFAULT instance. There is no mutable global state.

    /**
     * Validate a download URL for safety using the system DNS resolver.
     * Static convenience method for backward compatibility.
     */
    public static String validate(String url) {
        return DEFAULT.validateUrl(url);
    }

    /**
     * Returns true if the URL is safe to download from (system DNS).
     * Static convenience method for backward compatibility.
     */
    public static boolean isSafe(String url) {
        return validate(url) == null;
    }

    // ---- Core validation logic ----

    private static String doValidate(String url, DnsResolver resolver) {
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

        // Check if host is a literal IP address (without DNS resolution)
        if (isIpLiteral(host)) {
            try {
                InetAddress addr = InetAddress.getByName(host);
                String ipCheck = validateInetAddress(addr);
                if (ipCheck != null) return ipCheck;
            } catch (UnknownHostException e) {
                // Invalid IP literal
                return "Invalid IP address: " + host;
            }
        }

        // DNS resolution via this instance's resolver — fail closed
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
     * Checks if a host string is an IP literal (IPv4 or IPv6) without DNS resolution.
     * This prevents hostname strings from being resolved via system DNS in the
     * literal IP validation path.
     */
    private static boolean isIpLiteral(String host) {
        if (host == null || host.isEmpty()) return false;
        // IPv6 literal (contains colons)
        if (host.contains(":")) return true;
        // IPv4 literal: must be N.N.N.N where each N is a decimal number
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
        }
        return true;
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
