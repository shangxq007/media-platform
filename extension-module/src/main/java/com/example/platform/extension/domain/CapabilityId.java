package com.example.platform.extension.domain;

import java.util.List;
import java.util.Objects;

/**
 * #16 (R1/C16): typed namespaced CapabilityId.
 *
 * <p>A CapabilityId is a stable, implementation-neutral, provider-neutral,
 * plan-neutral identifier with an enforced namespace. Two namespaces exist:
 * <ul>
 *   <li><b>Platform reserved</b> — the platform owns prefixes such as
 *       {@code media.*}, {@code timeline.*}, {@code audio.*}, {@code video.*},
 *       {@code subtitle.*}, {@code render.*}. Vendors must not register here.</li>
 *   <li><b>Vendor extension</b> — third parties use reverse-DNS style
 *       ({@code com.vendor.*}, {@code org.vendor.*}).</li>
 * </ul>
 * Invalid namespaces fail closed at construction.
 */
public record CapabilityId(String value) {

    /** Platform-reserved namespace prefixes (C16). */
    public static final List<String> PLATFORM_RESERVED_PREFIXES = List.of(
            "media.", "timeline.", "audio.", "video.", "subtitle.", "render.");

    /** Vendor reverse-DNS prefixes (C16). */
    public static final List<String> VENDOR_PREFIXES = List.of(
            "com.", "org.", "net.", "io.", "dev.");

    public CapabilityId {
        Objects.requireNonNull(value, "value");
        if (!CapabilityNamespaceValidator.isValid(value)) {
            throw new IllegalArgumentException("invalid capability id namespace: " + value);
        }
    }

    public static CapabilityId of(String value) {
        return new CapabilityId(value);
    }

    /** True when the id lives in the platform-reserved namespace. */
    public boolean isPlatformReserved() {
        return PLATFORM_RESERVED_PREFIXES.stream().anyMatch(value::startsWith);
    }

    /** True when the id lives in a vendor extension namespace. */
    public boolean isVendorExtension() {
        return VENDOR_PREFIXES.stream().anyMatch(value::startsWith);
    }

    @Override
    public String toString() {
        return value;
    }
}
