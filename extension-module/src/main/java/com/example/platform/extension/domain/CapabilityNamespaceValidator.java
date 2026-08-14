package com.example.platform.extension.domain;

/**
 * #16 (R1/C16): capability id namespace validator.
 *
 * <p>Structural namespace validation — NOT a fixed TLD catalog and NOT an
 * ownership/trust registry. Rules (fail closed):
 * <ul>
 *   <li>Platform reserved ids must start with one of
 *       {@link CapabilityId#PLATFORM_RESERVED_PREFIXES} and be followed by at
 *       least one non-blank dot-separated segment.</li>
 *   <li>Vendor ids must be well-formed reverse-DNS style dotted names: at least
 *       two dot-separated segments, first segment starts with a letter, all
 *       segments lower-case alphanumeric (hyphens not allowed). The top-level
 *       segment is NOT restricted to a hardcoded TLD allowlist
 *       (com/org/net/io/dev/... are examples, not a boundary).</li>
 *   <li>Malformed ids (bare names, leading/trailing dots, double dots, upper
 *       case, underscores, hyphens, unknown prefixes) are rejected.</li>
 * </ul>
 */
public final class CapabilityNamespaceValidator {

    private CapabilityNamespaceValidator() {
    }

    /** Validates a capability id against platform-reserved and vendor namespaces. */
    public static boolean isValid(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        // structural base check (dotted, no empty/leading/trailing segments)
        if (!isWellFormedDottedName(id, true)) {
            return false;
        }
        // Platform reserved prefixes take precedence; a platform prefix with a
        // well-formed remainder is a valid (platform-owned) capability id.
        // Platform segments allow hyphens (e.g. subtitle.burn-in).
        for (String prefix : CapabilityId.PLATFORM_RESERVED_PREFIXES) {
            if (id.startsWith(prefix)) {
                return isWellFormedDottedName(id.substring(prefix.length()), true);
            }
        }
        // Vendor extension: reverse-DNS style — strictly lower-case alphanumeric
        // segments (no hyphens), at least two segments, first (top-level) segment
        // starts with a letter. No hardcoded TLD allowlist.
        String[] segments = id.split("\\.");
        if (segments.length < 2) {
            return false;
        }
        if (!Character.isLetter(segments[0].charAt(0))) {
            return false;
        }
        return isWellFormedDottedName(id, false);
    }

    /** @param allowHyphen platform namespaces allow hyphens; vendor reverse-DNS does not */
    private static boolean isWellFormedDottedName(String s, boolean allowHyphen) {
        if (s == null || s.isBlank() || s.startsWith(".") || s.endsWith(".") || s.contains("..")) {
            return false;
        }
        String pattern = allowHyphen ? "[a-z0-9-]+" : "[a-z0-9]+";
        for (String part : s.split("\\.")) {
            if (!part.matches(pattern) || part.startsWith("-") || part.endsWith("-")) {
                return false;
            }
        }
        return true;
    }
}
