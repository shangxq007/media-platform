package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FontIdPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "550e8400-e29b-41d4-a716-446655440000", // UUID
            "noto-sans-regular",
            "SourceHanSansSC-Regular",
            "MyFont.v1.2",
            "font_123",
            "a",
    })
    void validFontIdAccepted(String fontId) {
        assertDoesNotThrow(() -> FontIdPolicy.requireValidFontId(fontId));
        assertTrue(FontIdPolicy.isValidFontId(fontId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "font/../../secret",
            "font\\..\\secret",
            "file:///etc/passwd",
            "http://evil.com/font.ttf",
            "font\0malicious",
            "C:\\Windows\\Fonts\\evil.ttf",
            "%2e%2e/secret",
            "%2fetc/passwd",
            "",
            "   ",
    })
    void invalidFontIdRejected(String fontId) {
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId(fontId));
        assertFalse(FontIdPolicy.isValidFontId(fontId));
    }

    @Test
    void nullFontIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId(null));
    }

    @Test
    void tooLongFontIdRejected() {
        String longId = "a".repeat(200);
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId(longId));
    }

    @Test
    void fontIdWithSpacesRejected() {
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId("font with spaces"));
    }

    @Test
    void fontIdWithSpecialCharsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId("font<script>"));
        assertThrows(IllegalArgumentException.class, () -> FontIdPolicy.requireValidFontId("font;rm -rf"));
    }
}
