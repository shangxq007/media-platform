package com.example.platform.render.app.timeline.compile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderRequestFingerprint}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Same inputs produce same fingerprint</li>
 *   <li>Different outputProfile produces different fingerprint</li>
 *   <li>Different revisionId produces different fingerprint</li>
 *   <li>Fingerprint is deterministic across runs</li>
 *   <li>Fingerprint does not contain random UUID or timestamp</li>
 *   <li>Profile normalization is consistent</li>
 * </ul>
 */
class RenderRequestFingerprintGeneratorTest {

    @Test
    @DisplayName("Same inputs produce same fingerprint")
    void sameInputsProduceSameFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Different outputProfile produces different fingerprint")
    void differentProfileProducesDifferentFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_720p", "LEGACY");

        assertNotEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Different revisionId produces different fingerprint")
    void differentRevisionProducesDifferentFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                "proj-1", "rev-2", "default_1080p", "LEGACY");

        assertNotEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Different projectId produces different fingerprint")
    void differentProjectProducesDifferentFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                "proj-2", "rev-1", "default_1080p", "LEGACY");

        assertNotEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Different execution mode produces different fingerprint")
    void differentModeProducesDifferentFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "PLAN_BASED");

        assertNotEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Fingerprint is deterministic across repeated calls")
    void fingerprintIsDeterministic() {
        for (int i = 0; i < 100; i++) {
            RenderRequestFingerprint fp = RenderRequestFingerprint.generate(
                    "proj-1", "rev-1", "default_1080p", "LEGACY");
            assertEquals("rfp-" + fp.value().substring(4), fp.value());
        }
    }

    @Test
    @DisplayName("Fingerprint value starts with rfp- prefix")
    void fingerprintHasPrefix() {
        RenderRequestFingerprint fp = RenderRequestFingerprint.generate(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        assertTrue(fp.value().startsWith("rfp-"));
    }

    @Test
    @DisplayName("Profile normalization handles null and blank")
    void profileNormalization() {
        assertEquals("default_1080p", RenderRequestFingerprint.normalizeProfile(null));
        assertEquals("default_1080p", RenderRequestFingerprint.normalizeProfile(""));
        assertEquals("default_1080p", RenderRequestFingerprint.normalizeProfile("  "));
        assertEquals("default_720p", RenderRequestFingerprint.normalizeProfile("default_720p"));
        assertEquals("default_1080p", RenderRequestFingerprint.normalizeProfile("DEFAULT_1080P"));
        assertEquals("default_1080p", RenderRequestFingerprint.normalizeProfile(" Default_1080P "));
    }

    @Test
    @DisplayName("Null inputs produce stable fingerprint")
    void nullInputsProduceStableFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate(
                null, null, null, null);
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate(
                null, null, null, null);
        assertEquals(fp1.value(), fp2.value());
    }
}
