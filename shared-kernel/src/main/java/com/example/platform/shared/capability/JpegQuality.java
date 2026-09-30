package com.example.platform.shared.capability;

/**
 * Bounded JPEG quality (1..100).
 *
 * <p>Absence of a quality value in a capability parameter is a documented
 * contract default; the resolve stage materializes and pins the effective value
 * and a provider must never retain a hidden unpinned default.</p>
 */
public record JpegQuality(int value) {

    public JpegQuality {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("jpeg quality must be within 1..100: " + value);
        }
    }
}
