package com.example.platform.extension.domain;

/**
 * #16 (R1/C16): capability id namespace validator.
 *
 * <p>Rules (fail closed):
 * <ul>
 *   <li>Platform reserved ids must start with one of {@link CapabilityId#PLATFORM_RESERVED_PREFIXES}
 *       and be followed by a non-blank dot-separated segment.</li>
 *   <li>Vendor ids must start with one of {@link CapabilityId#VENDOR_PREFIXES} and contain at
 *       least two further dot-separated non-blank segments (reverse-DNS shape).</li>
 *   <li>Any other shape (bare names, unknown prefixes, vendor squatting a platform prefix,
 *       trailing/leading dots, double dots) is rejected.</li>
 * </ul>
 * No marketplace publisher registry is required for this bounded validation.
 */
public final class CapabilityNamespaceValidator {

    private CapabilityNamespaceValidator() {
    }

    /** Validates a capability id against platform-reserved and vendor namespaces. */
    public static boolean isValid(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (id.startsWith(".") || id.endsWith(".") || id.contains("..")) {
            return false;
        }
        for (String prefix : CapabilityId.PLATFORM_RESERVED_PREFIXES) {
            if (id.startsWith(prefix)) {
                String rest = id.substring(prefix.length());
                return isDottedSegment(rest);
            }
        }
        for (String prefix : CapabilityId.VENDOR_PREFIXES) {
            if (id.startsWith(prefix)) {
                // reverse-DNS: at least two further non-blank dot-separated segments
                String rest = id.substring(prefix.length());
                if (!isDottedSegment(rest)) {
                    return false;
                }
                return rest.split("\\.").length >= 2;
            }
        }
        return false;
    }

    private static boolean isDottedSegment(String s) {
        if (s == null || s.isBlank() || s.startsWith(".") || s.endsWith(".") || s.contains("..")) {
            return false;
        }
        for (String part : s.split("\\.")) {
            if (part.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
